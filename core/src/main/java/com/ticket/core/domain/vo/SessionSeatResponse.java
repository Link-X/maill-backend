package com.ticket.core.domain.vo;

import com.ticket.core.domain.entity.ShowSession;
import lombok.Data;
import java.util.List;

@Data
public class SessionSeatResponse {
    private ShowSession session;
    private List<AreaPriceVO> areaPriceList;
    private SeatSectionVO seatSection;

    // ---- 关联演出信息（避免前端再调一次 /api/show/{id}） ----
    private Long showId;
    private String showName;
    private String showVenue;
    private String showAddress;
    private String showCityCode;
    private String showCityName;
    private String showPosterUrl;
}
