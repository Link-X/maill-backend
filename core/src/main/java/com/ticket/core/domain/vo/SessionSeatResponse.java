package com.ticket.core.domain.vo;

import com.ticket.core.domain.entity.ShowSession;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Schema(description = "场次详情；座位图 + 价格区域 + 演出/城市概要")
@Data
public class SessionSeatResponse {
    @Schema(description = "场次原始信息") private ShowSession session;
    @Schema(description = "价格区域列表（areaId / price / originPrice）") private List<AreaPriceVO> areaPriceList;
    @Schema(description = "座位网格（rowCount × colCount，含实时可售状态）") private SeatSectionVO seatSection;

    @Schema(description = "演出 ID", example = "1") private Long showId;
    @Schema(description = "演出名称", example = "周杰伦嘉年华") private String showName;
    @Schema(description = "演出场馆") private String showVenue;
    @Schema(description = "演出地址") private String showAddress;
    @Schema(description = "演出城市代码", example = "310000") private String showCityCode;
    @Schema(description = "演出城市名", example = "上海") private String showCityName;
    @Schema(description = "演出海报 URL") private String showPosterUrl;
}
