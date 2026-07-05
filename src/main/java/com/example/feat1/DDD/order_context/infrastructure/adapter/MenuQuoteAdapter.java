package com.example.feat1.DDD.order_context.infrastructure.adapter;

import com.example.feat1.DDD.menu_context.application.MenuOrderValidationService;
import com.example.feat1.DDD.menu_context.application.dto.MenuSelectionDtos.MenuSelectionRequest;
import com.example.feat1.DDD.menu_context.domain.model.MenuDomainException;
import com.example.feat1.DDD.order_context.domain.port.MenuQuotePort;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderMenuQuote;
import com.example.feat1.DDD.order_context.domain.snapshot.OrderToppingSnapshot;
import com.example.feat1.common.exception.AppException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MenuQuoteAdapter implements MenuQuotePort {
  private final MenuOrderValidationService menuOrderValidationService;

  @Override
  public OrderMenuQuote quote(UUID dishId, List<UUID> toppingOptionIds) {
    try {
      var quote =
          menuOrderValidationService.validateAndQuote(
              new MenuSelectionRequest(dishId, toppingOptionIds));
      return new OrderMenuQuote(
          quote.dishId(),
          quote.dishName(),
          quote.basePrice(),
          quote.selectedToppings().stream()
              .map(
                  topping ->
                      new OrderToppingSnapshot(
                          topping.toppingGroupId(),
                          topping.toppingGroupName(),
                          topping.toppingOptionId(),
                          topping.toppingOptionName(),
                          topping.additionalPrice()))
              .toList(),
          quote.toppingsTotal(),
          quote.totalPrice());
    } catch (MenuDomainException exception) {
      throw new AppException(exception.getCode(), exception.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
