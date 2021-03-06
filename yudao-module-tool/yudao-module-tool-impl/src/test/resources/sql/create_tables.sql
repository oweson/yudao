CREATE TABLE IF NOT EXISTS "tool_test_demo" (
    "id"          bigint       NOT NULL GENERATED BY DEFAULT AS IDENTITY,
    "name"        varchar(100) NOT NULL,
    "status"      tinyint      NOT NULL,
    "type"        tinyint      NOT NULL,
    "category"    tinyint      NOT NULL,
    "remark"      varchar(500),
    "creator"     varchar(64)           DEFAULT '''',
    "create_time" datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updater"     varchar(64)           DEFAULT '''',
    "update_time" datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    "deleted"     bit          NOT NULL DEFAULT FALSE,
    PRIMARY KEY ("id")
) COMMENT '字典类型表';
