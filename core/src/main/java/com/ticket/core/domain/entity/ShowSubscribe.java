package com.ticket.core.domain.entity;
import com.ticket.core.domain.vo.ShowVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;

@Schema(description = "演出开售提醒订阅")
@Data
public class ShowSubscribe {
    @Schema(description = "订阅 ID") private Long id;
    @Schema(description = "用户 ID") private Long userId;
    @Schema(description = "演出 ID") private Long showId;
    @Schema(description = "提前提醒分钟数(默认 10)") private Integer notifyBeforeMinutes;
    @Schema(description = "已推送提前提醒") private Integer notifiedPre;
    @Schema(description = "已推送开售") private Integer notifiedOpen;
    @Schema(description = "创建时间") private LocalDateTime createTime;
    @Schema(description = "更新时间") private LocalDateTime updateTime;
    @Schema(description = "演出(join 注入)") private ShowVO show;
}
