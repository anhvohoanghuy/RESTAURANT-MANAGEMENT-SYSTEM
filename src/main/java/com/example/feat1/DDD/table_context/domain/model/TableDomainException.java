package com.example.feat1.DDD.table_context.domain.model;

public class TableDomainException extends RuntimeException {
  public static final String TABLE_NOT_ORDERABLE = "TABLE_NOT_ORDERABLE";
  public static final String TABLE_AREA_NOT_ORDERABLE = "TABLE_AREA_NOT_ORDERABLE";

  private final String code;

  public TableDomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static TableDomainException tableNotOrderable() {
    return new TableDomainException(TABLE_NOT_ORDERABLE, "Table is not orderable");
  }

  public static TableDomainException tableAreaNotOrderable() {
    return new TableDomainException(TABLE_AREA_NOT_ORDERABLE, "Table area is not orderable");
  }
}
