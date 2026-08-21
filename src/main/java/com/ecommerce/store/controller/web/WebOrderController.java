package com.ecommerce.store.controller.web;

import com.ecommerce.store.dto.request.CheckoutRequest;
import com.ecommerce.store.service.CartService;
import com.ecommerce.store.service.OrderService;
import com.ecommerce.store.service.PaymentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebOrderController {

    private final CartService cartService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public WebOrderController(CartService cartService, OrderService orderService,
                             PaymentService paymentService) {
        this.cartService = cartService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping("/checkout")
    public String checkoutPage(Model model) {
        model.addAttribute("cart", cartService.view());
        return "pages/checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(@RequestParam String street,
                             @RequestParam String city,
                             @RequestParam String zip) {
        orderService.checkout(new CheckoutRequest(street, city, zip));
        return "redirect:/orders";
    }

    @GetMapping("/orders")
    public String orderHistory(Model model) {
        model.addAttribute("orders", orderService.history());
        return "pages/order-history";
    }

    /** Mock payment: settle a pending order, then return to history. */
    @PostMapping("/orders/{orderNumber}/pay")
    public String pay(@PathVariable String orderNumber) {
        paymentService.confirmMockPaymentByNumber(orderNumber);
        return "redirect:/orders";
    }
}
