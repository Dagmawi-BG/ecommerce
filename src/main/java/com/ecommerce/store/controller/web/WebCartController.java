package com.ecommerce.store.controller.web;

import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.exception.InvalidCouponException;
import com.ecommerce.store.service.CartService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cart")
public class WebCartController {

    private final CartService cartService;

    public WebCartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public String view(Model model) {
        model.addAttribute("cart", cartService.view());
        return "pages/cart";
    }

    @PostMapping("/items")
    public String addItem(@RequestParam Long productId,
                          @RequestParam(defaultValue = "1") int quantity) {
        cartService.addItem(new CartItemRequest(productId, quantity));
        return "redirect:/cart";
    }

    @PostMapping("/items/{productId}/remove")
    public String removeItem(@PathVariable Long productId) {
        cartService.removeItem(productId);
        return "redirect:/cart";
    }

    @PostMapping("/coupon")
    public String applyCoupon(@RequestParam String code, RedirectAttributes redirectAttributes) {
        try {
            cartService.applyCoupon(code);
        } catch (InvalidCouponException e) {
            redirectAttributes.addFlashAttribute("couponError", e.getMessage());
        }
        return "redirect:/cart";
    }
}
