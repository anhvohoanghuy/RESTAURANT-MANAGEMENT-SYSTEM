package com.example.feat1.DDD.menu_context.domain.model;

public class MenuDomainException extends RuntimeException {
  public static final String DISH_NOT_ORDERABLE = "MENU_DISH_NOT_ORDERABLE";
  public static final String TOPPING_NOT_ORDERABLE = "MENU_TOPPING_NOT_ORDERABLE";
  public static final String TOPPING_NOT_IN_DISH = "MENU_TOPPING_NOT_IN_DISH";
  public static final String TOPPING_GROUP_REQUIRED = "MENU_TOPPING_GROUP_REQUIRED";
  public static final String TOPPING_GROUP_LIMIT_EXCEEDED = "MENU_TOPPING_GROUP_LIMIT_EXCEEDED";

  private final String code;

  public MenuDomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static MenuDomainException dishNotOrderable() {
    return new MenuDomainException(DISH_NOT_ORDERABLE, "Dish is not orderable");
  }

  public static MenuDomainException toppingNotOrderable() {
    return new MenuDomainException(TOPPING_NOT_ORDERABLE, "Topping is not orderable");
  }

  public static MenuDomainException toppingNotInDish() {
    return new MenuDomainException(TOPPING_NOT_IN_DISH, "Topping does not belong to dish");
  }

  public static MenuDomainException toppingGroupRequired(String groupName) {
    return new MenuDomainException(
        TOPPING_GROUP_REQUIRED, "Topping group is required: " + groupName);
  }

  public static MenuDomainException toppingGroupLimitExceeded(String groupName) {
    return new MenuDomainException(
        TOPPING_GROUP_LIMIT_EXCEEDED, "Topping group limit exceeded: " + groupName);
  }
}
