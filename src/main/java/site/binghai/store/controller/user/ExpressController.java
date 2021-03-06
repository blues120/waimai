package site.binghai.store.controller.user;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;
import site.binghai.store.config.IceConfig;
import site.binghai.store.controller.AfterPay;
import site.binghai.store.controller.BaseController;
import site.binghai.store.entity.*;
import site.binghai.store.enums.BookingTypeEnum;
import site.binghai.store.enums.OrderStatusEnum;
import site.binghai.store.enums.PayBizEnum;
import site.binghai.store.service.*;
import site.binghai.store.tools.MD5;
import site.binghai.store.tools.TimeTools;
import site.binghai.store.tools.TplGenerator;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Created by IceSea on 2018/4/27.
 * GitHub: https://github.com/IceSeaOnly
 */
@RequestMapping("user")
@Controller
public class ExpressController extends BaseController {
    @Autowired
    private UnifiedOrderService unifiedOrderService;
    @Autowired
    private ExpressOrderService expressOrderService;
    @Autowired
    private RegionConfigService regionConfigService;
    @Autowired
    private UserService userService;
    @Autowired
    private CityService cityService;
    @Autowired
    private WxService wxService;
    @Autowired
    private IceConfig iceConfig;
    @Autowired
    private ManagerService managerService;
    @Autowired
    private BookPeriodService bookPeriodService;
    @Autowired
    private AddressService addressService;

    @RequestMapping("exIndex")
    public String index(ModelMap map) {
        map.put("regionConfig", regionConfigService.findByRegionId(getUser().getRegionId()));
        map.put("orders", expressOrderService.findAllByUserId(getUser()));
        map.put("deliver_periods", bookPeriodService.getPeriods(getUser().getRegionId(), BookingTypeEnum.DELIVER_EXPRESS));
        map.put("fetch_periods", bookPeriodService.getPeriods(getUser().getRegionId(), BookingTypeEnum.FETCH_EXPRESS));
        map.put("provinces", cityService.getAllProvince());
        return "expressBiz";
    }

    @RequestMapping("getCityByProvince")
    @ResponseBody
    public Object getCityByProvince(@RequestParam Long id) {
        City city = cityService.findById(id);
        return cityService.getByProvince(city.getProvince());
    }

    @RequestMapping("getCity")
    @ResponseBody
    public Object getCity(@RequestParam Long id) {
        return cityService.findById(id);
    }

    @PostMapping("newDelivery")
    public String newDelivery(@RequestParam String to,
                              @RequestParam String toPhone,
                              @RequestParam String from,
                              @RequestParam String fromPhone,
//                              @RequestParam Double fee,
                              @RequestParam String toWhere,
                              @RequestParam String whatIs,
                              @RequestParam String personalId,
                              @RequestParam Long bookPeriod,
                              @RequestParam Long city,
                              ModelMap map
    ) {
        if (city < 0 || !noEmptyString(Arrays.asList(to, toPhone, from, fromPhone, toWhere, personalId))) {
            return commonResp("输入有误", "输入不正确，请确认输入完整", "好的", "/user/exIndex", map);
        }

        if (fromPhone.length() != 11) {
            return commonResp("输入有误", "手机号不正确，请输入正确的11位手机号,不要带空格" + fromPhone, "好的", "/user/exIndex", map);
        }

        double fee = 999999;
        if (fee <= 0) {
            return commonResp("价格有误", "请输入正确的价格", "好的", "/user/exIndex", map);
        }

        UnifiedOrder unifiedOrder = unifiedOrderService.newOrder(PayBizEnum.EXPRESS, getUser(), "寄快递", Double.valueOf(fee * 100).intValue());
        unifiedOrder.setUserName(from);
        ExpressOrder expressOrder = new ExpressOrder();
        expressOrder.setPriceConfirmed(Boolean.FALSE);
        expressOrder.setUserName(from);
        expressOrder.setUserId(getUser().getId());
        expressOrder.setHasPay(false);
        expressOrder.setCanceled(false);
        expressOrder.setUnifiedId(unifiedOrder.getId());
        expressOrder.setTo(to);
        expressOrder.setToPhone(toPhone);
        expressOrder.setFrom(from);
        expressOrder.setFromPhone(fromPhone);
        expressOrder.setPersonalId(personalId);
        expressOrder.setType(0);
        City c = cityService.findById(city);
        expressOrder.setToWhere(c.getProvince() + c.getCity() + toWhere);
        expressOrder.setWhatIs(whatIs);
        expressOrder.setBookPeriod(bookPeriodService.findById(bookPeriod).getName());

        expressOrder = expressOrderService.save(expressOrder);

        ExpressOrder finalExpressOrder = expressOrder;
        managerService.findByRegionId(unifiedOrder.getRegionId())
                .forEach(v -> commitSuccess(v, finalExpressOrder));

        wxService.tplMessage(iceConfig.getOrderAccept(), TplGenerator.getInstance()
                        .put("first", "您的寄件订单已经接单，请耐心等待客服联系您!")
                        .put("keyword1", unifiedOrder.getCreated() + "")
                        .put("keyword2", TimeTools.now())
                        .put("remark", "您的寄件订单已经接单，请耐心等待客服联系，或者您也可以联系客服 18954289568 ，感谢您的使用！")
                        .getAll(),
                getUser().getOpenId(),
                iceConfig.getServer() + "/user/confirmExpressOrder?unifiedId=" + expressOrder.getUnifiedId());

        return "redirect:/user/confirmExpressOrder?unifiedId=" + expressOrder.getUnifiedId();
    }

    @PostMapping("newFetchThing")
    public String newFetchThing(@RequestParam String from,
                                @RequestParam String fromPhone,
                                @RequestParam String sms,
                                @RequestParam String toWhere,
                                @RequestParam Long bookPeriod,
                                ModelMap map) {
        if (!noEmptyString(Arrays.asList(from, fromPhone, sms))) {
            return commonResp("输入有误", "输入不正确，请确认输入完整", "好的", "/user/exIndex", map);
        }
        RegionConfig config = regionConfigService.findByRegionId(getUser().getRegionId());
        UnifiedOrder unifiedOrder = unifiedOrderService.newOrder(PayBizEnum.EXPRESS, getUser(), "取快递", (config == null || config.getFetchFee() == null) ? 500 : config.getFetchFee());

        ExpressOrder expressOrder = new ExpressOrder();
        expressOrder.setPriceConfirmed(Boolean.TRUE);
        expressOrder.setUserName(getUser().getUserName());
        expressOrder.setUserId(getUser().getId());
        expressOrder.setHasPay(false);
        expressOrder.setCanceled(false);
        expressOrder.setUnifiedId(unifiedOrder.getId());
        expressOrder.setTo(from);
        expressOrder.setToPhone(fromPhone);
        expressOrder.setFrom(from);
        expressOrder.setFromPhone(fromPhone);
        expressOrder.setType(1);
        expressOrder.setSms(sms);
        expressOrder.setToWhere(toWhere);
        expressOrder.setBookPeriod(bookPeriodService.findById(bookPeriod).getName());

        expressOrder = expressOrderService.save(expressOrder);

        wxService.tplMessage(iceConfig.getOrderAccept(), TplGenerator.getInstance()
                        .put("first", "您的代取订单已经接单，请耐心等待客服联系您!")
                        .put("keyword1", unifiedOrder.getCreated() + "")
                        .put("keyword2", TimeTools.now())
                        .put("remark", "您的代取订单已经接单，请耐心等待客服联系，或者您也可以联系客服 18954289568 ，感谢您的使用！")
                        .getAll(),
                getUser().getOpenId(),
                iceConfig.getServer() + "/user/confirmExpressOrder?unifiedId=" + expressOrder.getUnifiedId());

        return "redirect:/user/confirmExpressOrder?unifiedId=" + expressOrder.getUnifiedId();
    }

    private void commitSuccess(Manager manager, ExpressOrder order) {
        wxService.tplMessage(iceConfig.getNewOrderNoticeTpl(), TplGenerator.getInstance()
                .put("first", "新" + (order.getType() == 0 ? "寄件" : "取件") + "订单到达!用户已下单，请联系用户确定费用，点击设定价格")
                .put("keyword1", (order.getType() == 0 ? "寄件" : "取件"))
                .put("keyword2", TimeTools.now())
                .put("keyword3", "Id" + order.getUserId())
                .put("keyword4", order.getFrom())
                .put("keyword5", "待确认")
                .put("remark", "点击设定价格")
                .getAll(), manager.getOpenId(), iceConfig.getServer() + "/user/adminExpressOrderList?type=0&validate=" + MD5.encryption(manager.getOpenId()));
    }

    @PostMapping("setPrice4ExpressOrder")
    public String setPrice4ExpressOrder(@RequestParam Long id, @RequestParam Double setPrice, ModelMap map) {
        ExpressOrder order = expressOrderService.findById(id);
        order.setPriceConfirmed(Boolean.TRUE);
        UnifiedOrder unifiedOrder = unifiedOrderService.findById(order.getUnifiedId());
        unifiedOrder.setShouldPay(Double.valueOf(setPrice * 100).intValue());
        unifiedOrder.setOriginalPrice(unifiedOrder.getShouldPay());
        unifiedOrderService.update(unifiedOrder);
        expressOrderService.update(order);
        wxService.tplMessage(iceConfig.getRequire2Pay(), TplGenerator.getInstance()
                .put("first", "订单支付已确认,请继续支付")
                .put("keyword1", unifiedOrder.getOrderId())
                .put("keyword2", unifiedOrder.originalDoublePrice() + "元")
                .put("keyword3", unifiedOrder.getCreatedTime())
                .put("remark", "点击进入支付页")
                .getAll(), unifiedOrder.getOpenId(), iceConfig.getServer() + "/user/confirmExpressOrder?unifiedId=" + unifiedOrder.getId());

        return commonResp("设定完毕", "请用户继续支付", "好的", "/user/index", map);
    }


    @RequestMapping("isOrderPriceConfirmed")
    @ResponseBody
    public Object isOrderPriceConfirmed(@RequestParam Long orderId) {
        ExpressOrder order = expressOrderService.findById(orderId);
        if (order != null && order.getPriceConfirmed()) {
            return 1;
        }
        return 0;
    }


    @GetMapping("confirmExpressOrder")
    public String confirmExpressOrder(@RequestParam Long unifiedId, ModelMap map) {
        ExpressOrder order = expressOrderService.findByUnifiedId(unifiedId);
        if (order == null || !order.getUserId().equals(getUser().getId())) {
            return commonResp("非法参数", "非法参数", "好的", "/user/index", map);
        }
        UnifiedOrder unifiedOrder = unifiedOrderService.findById(order.getUnifiedId());
        int type = order.getType();
        StringBuilder sb = new StringBuilder();

        UserAddress address = addressService.getUserAddress(order.getUserId());

        map.put("title", type == 0 ? "寄快递" : "取快递");
        if (type == 0) {
            sb.append("寄件人: " + order.getFrom() + "</br>");
            sb.append("寄件地址:" + address.getAddressHead() + address.getAddressDetail() + "<br/>");
            sb.append(String.format("寄件人手机: <a href=\"tel:%s\">%s</a></br>", address.getUserPhone(), address.getUserPhone()));
            sb.append("收件人 :" + order.getTo() + "</br>");
            sb.append(String.format("收件人手机: <a href=\"tel:%s\">%s</a></br>", order.getToPhone(), order.getToPhone()));
            sb.append("收件地址: " + order.getToWhere() + "</br>");
            sb.append("预约时间: " + order.getBookPeriod() + "</br>");
            sb.append("内容物: " + order.getWhatIs() + "</br>");
            sb.append("身份证号:" + order.getPersonalId() + "</br>");
            sb.append("快递名:" + order.getExName() + "</br>");
            sb.append("快递单号:" + order.getExNo() + "</br>");
        } else {
            sb.append("收件人: " + order.getFrom() + "</br>");
            sb.append(String.format("收件手机: <a href=\"tel:%s\">%s</a></br>", order.getToPhone(), order.getToPhone()));
            sb.append("短信内容: " + order.getSms() + "</br>");
            sb.append("配送地址: " + order.getToWhere() + "</br>");
            sb.append("预约时间: " + order.getBookPeriod() + "</br>");
        }

        map.put("uorder", unifiedOrder);
        map.put("order", order);
        map.put("detail", sb.toString());
        map.put("adminTag", false);
        map.put("isOwner", true);
        return "confirmExpressOrder";
    }

    /**
     * 管理员访问路径
     */
    @GetMapping("orderDetail")
    public String unifiedOrderDetail(@RequestParam Long unifiedId, @RequestParam String openid, ModelMap map) {
        if (!openid.equals(getUser().getOpenId())) {
            return commonResp("无权查看", "对不起,您无权查看此信息", "好的", "/user/index", map);
        }

        ExpressOrder order = expressOrderService.findByUnifiedId(unifiedId);
        if (order == null) {
            return commonResp("非法参数", "非法参数", "好的", "/user/Index", map);
        }
        UnifiedOrder unifiedOrder = unifiedOrderService.findById(order.getUnifiedId());
        int type = order.getType();
        StringBuilder sb = new StringBuilder();

        map.put("title", type == 0 ? "寄快递" : "取快递");
        UserAddress address = addressService.getUserAddress(order.getUserId());
        if (type == 0) {
            sb.append("寄件人:" + order.getFrom() + "</br>");
            sb.append("寄件地址:" + address.getAddressHead() + address.getAddressDetail() + "<br/>");
            sb.append(String.format("寄件人手机:<a href=\"tel:%s\">%s</a></br>", address.getUserPhone(), address.getUserPhone()));
            sb.append("收件人:" + order.getTo() + "</br>");
            sb.append(String.format("收件人手机:<a href=\"tel:%s\">%s</a></br>", order.getToPhone(), order.getToPhone()));
            sb.append("收件地址:" + order.getToWhere() + "</br>");
            sb.append("预约时间:" + order.getBookPeriod() + "</br>");
            sb.append("内容物:" + order.getWhatIs() + "</br>");
            sb.append("身份证号:" + order.getPersonalId() + "</br>");
            sb.append("快递名:" + order.getExName() + "</br>");
            sb.append("快递单号:" + order.getExNo() + "</br>");
        } else {
            sb.append("收件人:" + order.getFrom() + "</br>");
            sb.append(String.format("收件手机:<a href=\"tel:%s\">%s</a></br>", order.getToPhone(), order.getToPhone()));
            sb.append("短信内容:" + order.getSms() + "</br>");
            sb.append("配送地址:" + order.getToWhere() + "</br>");
            sb.append("预约时间:" + order.getBookPeriod() + "</br>");
        }

        map.put("uorder", unifiedOrder);
        map.put("order", order);
        map.put("detail", sb.toString());
        map.put("adminTag", true);
        map.put("isOwner", false);
        map.put("scanUrl", iceConfig.getPayServer().substring(0, iceConfig.getPayServer().length() - 10) + "/scanBarCode.php?exId=" + order.getId() + "&passCode=" + order.getCreated());
        String printKey = UUID.randomUUID().toString();
        getSession().setAttribute("printKey", printKey);
        map.put("printKey", printKey);
        return "confirmExpressOrder";
    }

    @Autowired
    private AfterPay afterPay;

    @RequestMapping("confirmOfflinePay")
    public String confirmOfflinePay(@RequestParam Long id, ModelMap map) {
        ExpressOrder order = expressOrderService.findById(id);
        UnifiedOrder unifiedOrder = unifiedOrderService.findById(order.getUnifiedId());
        Boolean isManager = managerService.findByRegionId(unifiedOrder.getRegionId()).stream()
                .anyMatch(v -> v.getOpenId().equals(getUser().getOpenId()));

        if (!isManager) {
            return commonResp("非法访问", "非法访问", "返回", "/user/index", map);
        }
        order.setHasPay(Boolean.TRUE);
        order.setPriceConfirmed(Boolean.TRUE);
        expressOrderService.update(order);
        unifiedOrder.setShouldPay(0);
        unifiedOrder.setStatus(OrderStatusEnum.PAIED.getCode());
        unifiedOrder.setOrderId("OFFLINE_" + TimeTools.currentTS());
        unifiedOrderService.update(unifiedOrder);
        afterPay.afterPay(unifiedOrder.getOrderId(), MD5.encryption(unifiedOrder.getOpenId()));
        return commonResp("设定完毕", "已确认线下支付", "好的", "/user/index", map);
    }
}
