package com.ticket.core.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "座位网格的一个格子（type=0 时是占位空格，无可购买座位）")
@Data
public class SeatColVO {
    @Schema(description = "座位数据库 ID；空位时为空字符串") private String colId;
    @Schema(description = "列号显示文本；空位时为空字符串") private String colNum;
    @Schema(description = "座位名称，如 '1排01座'；空位时 null", example = "1排01座") private String seatName;
    @Schema(description = "座位类型 0=占位空格 1=普通 2=情侣左 3=情侣右", example = "1", allowableValues = {"0","1","2","3"})
    private Integer type;
    @Schema(description = "价格区域 ID；空位为 null", example = "1") private String areaId;
    @Schema(description = "实时状态 0=可售 1=已锁 2=已售；type=0 为 null", example = "0", allowableValues = {"0","1","2"})
    private Integer status;
}
