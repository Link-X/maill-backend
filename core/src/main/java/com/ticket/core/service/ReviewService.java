package com.ticket.core.service;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Order;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.domain.entity.ShowReview;
import com.ticket.core.domain.entity.ShowReviewImage;
import com.ticket.core.domain.entity.ShowReviewReport;
import com.ticket.core.mapper.OrderMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mapper.ShowReviewImageMapper;
import com.ticket.core.mapper.ShowReviewLikeMapper;
import com.ticket.core.mapper.ShowReviewMapper;
import com.ticket.core.mapper.ShowReviewReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    public static final int REVIEW_MODE_NONE = 0;
    public static final int REVIEW_MODE_ALL = 1;
    public static final int REVIEW_MODE_WATCHED = 2;

    public static final int STATUS_NORMAL = 0;
    public static final int STATUS_REPORTED = 1;
    public static final int STATUS_HIDDEN = 2;

    private final ShowMapper showMapper;
    private final ShowReviewMapper reviewMapper;
    private final ShowReviewImageMapper imageMapper;
    private final ShowReviewLikeMapper likeMapper;
    private final ShowReviewReportMapper reportMapper;
    private final OrderMapper orderMapper;

    public ReviewService(ShowMapper showMapper,
                         ShowReviewMapper reviewMapper,
                         ShowReviewImageMapper imageMapper,
                         ShowReviewLikeMapper likeMapper,
                         ShowReviewReportMapper reportMapper,
                         OrderMapper orderMapper) {
        this.showMapper = showMapper;
        this.reviewMapper = reviewMapper;
        this.imageMapper = imageMapper;
        this.likeMapper = likeMapper;
        this.reportMapper = reportMapper;
        this.orderMapper = orderMapper;
    }

    // ----- 权限 -----

    /** 检查用户能否评价该演出。返回 true=可评 false=不可评(无权或关闭)。也可用 throw 形式。 */
    public boolean canReview(Long userId, Long showId, Long orderId) {
        Show s = showMapper.selectById(showId);
        if (s == null) throw new BusinessException(ErrorCode.SHOW_NOT_FOUND);
        Integer mode = s.getReviewMode() == null ? REVIEW_MODE_ALL : s.getReviewMode();
        if (mode == REVIEW_MODE_NONE) return false;
        if (mode == REVIEW_MODE_ALL) return true;
        // REVIEW_MODE_WATCHED: 必须有归属本用户的已观看订单(已支付/部分退款)
        if (orderId == null) return false;
        Order o = orderMapper.selectById(orderId);
        if (o == null) return false;
        if (!o.getUserId().equals(userId)) return false;
        Integer st = o.getStatus();
        // 已支付(1) 或 部分退款(5) 视为已观看；未支付/已取消/退款中/已退款均不允许
        return st != null && (st == 1 || st == 5);
    }

    // ----- 发布 -----

    @Transactional
    public ShowReview publish(Long userId, Long showId, Long orderId, Integer rating,
                              String content, List<String> imageUrls) {
        Show s = showMapper.selectById(showId);
        if (s == null) throw new BusinessException(ErrorCode.SHOW_NOT_FOUND);
        Integer mode = s.getReviewMode() == null ? REVIEW_MODE_ALL : s.getReviewMode();
        if (mode == REVIEW_MODE_NONE) throw new BusinessException(ErrorCode.REVIEW_DISABLED);
        if (mode == REVIEW_MODE_WATCHED && !canReview(userId, showId, orderId)) {
            throw new BusinessException(ErrorCode.REVIEW_NO_PERMISSION);
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new BusinessException(ErrorCode.REVIEW_RATING_REQUIRED);
        }
        LocalDateTime now = LocalDateTime.now();
        ShowReview r = new ShowReview();
        r.setShowId(showId);
        r.setUserId(userId);
        r.setOrderId(orderId);
        r.setParentId(null);
        r.setReplyToUserId(null);
        r.setContent(content);
        r.setRating(rating);
        r.setLikeCount(0);
        r.setReplyCount(0);
        r.setStatus(STATUS_NORMAL);
        r.setCreateTime(now);
        r.setUpdateTime(now);
        reviewMapper.insert(r);
        attachImages(r.getId(), imageUrls);
        refreshShowRating(showId);
        return r;
    }

    @Transactional
    public ShowReview reply(Long userId, Long parentId, Long replyToUserId,
                            String content, List<String> imageUrls) {
        ShowReview parent = reviewMapper.selectById(parentId);
        if (parent == null) throw new BusinessException(ErrorCode.REVIEW_PARENT_NOT_FOUND);
        if (parent.getParentId() != null) {
            // 楼中楼:回复指向二级回复时,也归到 parent 的一级
            parent = reviewMapper.selectById(parent.getParentId());
            if (parent == null) throw new BusinessException(ErrorCode.REVIEW_PARENT_NOT_FOUND);
        }
        // 权限同 publish:与一级评论相同的 reviewMode 规则
        Show s = showMapper.selectById(parent.getShowId());
        if (s == null) throw new BusinessException(ErrorCode.SHOW_NOT_FOUND);
        Integer mode = s.getReviewMode() == null ? REVIEW_MODE_ALL : s.getReviewMode();
        if (mode == REVIEW_MODE_NONE) throw new BusinessException(ErrorCode.REVIEW_DISABLED);
        if (mode == REVIEW_MODE_WATCHED) {
            // 必须在该 show 下持有有效票券(status=1 已支付 或 status=5 部分退款)
            if (orderMapper.existsCompletedByUserAndShow(userId, parent.getShowId()) <= 0) {
                throw new BusinessException(ErrorCode.REVIEW_NO_PERMISSION);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        ShowReview r = new ShowReview();
        r.setShowId(parent.getShowId());
        r.setUserId(userId);
        r.setOrderId(null);
        r.setParentId(parent.getId());
        r.setReplyToUserId(replyToUserId);
        r.setContent(content);
        r.setRating(null);
        r.setLikeCount(0);
        r.setReplyCount(0);
        r.setStatus(STATUS_NORMAL);
        r.setCreateTime(now);
        r.setUpdateTime(now);
        reviewMapper.insert(r);
        attachImages(r.getId(), imageUrls);
        reviewMapper.incrReplyCount(parent.getId());
        return r;
    }

    private void attachImages(Long reviewId, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        List<ShowReviewImage> list = new ArrayList<>();
        int sort = 0;
        for (String url : urls) {
            ShowReviewImage img = new ShowReviewImage();
            img.setReviewId(reviewId);
            img.setUrl(url);
            img.setSort(sort++);
            list.add(img);
        }
        imageMapper.batchInsert(list);
    }

    // ----- 列表 -----

    public Map<String, Object> listFirstLevel(Long showId, String sort, Long viewerUserId,
                                              Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowReview> list = reviewMapper.selectFirstLevel(showId, sort, false, offset, size);
        attachImagesAndLikes(list, viewerUserId);
        int total = reviewMapper.countFirstLevel(showId, false);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    public Map<String, Object> listReplies(Long parentId, Long viewerUserId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowReview> list = reviewMapper.selectReplies(parentId, offset, size);
        attachImagesAndLikes(list, viewerUserId);
        int total = reviewMapper.countReplies(parentId);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        return r;
    }

    public ShowReview getDetail(Long id, Long viewerUserId) {
        ShowReview r = reviewMapper.selectById(id);
        if (r == null) return null;
        attachImagesAndLikes(Collections.singletonList(r), viewerUserId);
        return r;
    }

    private void attachImagesAndLikes(List<ShowReview> list, Long viewerUserId) {
        if (list == null || list.isEmpty()) return;
        List<Long> ids = list.stream().map(ShowReview::getId).collect(Collectors.toList());
        List<ShowReviewImage> allImages = imageMapper.selectByReviewIds(ids);
        Map<Long, List<ShowReviewImage>> grouped = new HashMap<>();
        for (ShowReviewImage im : allImages) {
            grouped.computeIfAbsent(im.getReviewId(), k -> new ArrayList<>()).add(im);
        }
        Set<Long> likedSet = Collections.emptySet();
        if (viewerUserId != null) {
            likedSet = new HashSet<>(likeMapper.selectLikedReviewIds(viewerUserId, ids));
        }
        for (ShowReview r : list) {
            r.setImages(grouped.getOrDefault(r.getId(), Collections.emptyList()));
            r.setLiked(likedSet.contains(r.getId()));
        }
    }

    public Map<String, Object> listByUser(Long userId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowReview> list = reviewMapper.selectByUser(userId, offset, size);
        attachImagesAndLikes(list, userId);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        return r;
    }

    // ----- 点赞 -----

    @Transactional
    public boolean like(Long userId, Long reviewId) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        int inserted = likeMapper.insertIgnore(reviewId, userId);
        if (inserted > 0) {
            reviewMapper.incrLikeCount(reviewId);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean unlike(Long userId, Long reviewId) {
        int deleted = likeMapper.delete(reviewId, userId);
        if (deleted > 0) {
            reviewMapper.decrLikeCount(reviewId);
            return true;
        }
        return false;
    }

    // ----- 举报 -----

    @Transactional
    public ShowReviewReport report(Long userId, Long reviewId, String reason) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        // 幂等：同一用户对同一评论只能举报一次
        if (reportMapper.countByReporterAndReview(userId, reviewId) > 0) {
            throw new BusinessException(ErrorCode.REVIEW_REPORT_DUPLICATED);
        }
        ShowReviewReport rep = new ShowReviewReport();
        rep.setReviewId(reviewId);
        rep.setReporterId(userId);
        rep.setReason(reason);
        rep.setStatus(0);
        reportMapper.insert(rep);
        // 标记评论为举报中(但不隐藏)
        if (r.getStatus() != null && r.getStatus() == STATUS_NORMAL) {
            reviewMapper.updateStatus(reviewId, STATUS_REPORTED, LocalDateTime.now());
        }
        return rep;
    }

    // ----- 用户删除自己的评价 -----

    @Transactional
    public void deleteByUser(Long userId, Long reviewId) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        if (!r.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.REVIEW_DELETE_FORBIDDEN);
        }
        Long parentId = r.getParentId();
        Long showId = r.getShowId();
        imageMapper.deleteByReviewId(reviewId);
        reviewMapper.deleteById(reviewId);
        if (parentId != null) {
            reviewMapper.decrReplyCount(parentId);
        } else {
            refreshShowRating(showId);
        }
    }

    // ----- admin -----

    @Transactional
    public void adminHide(Long reviewId) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        reviewMapper.updateStatus(reviewId, STATUS_HIDDEN, LocalDateTime.now());
        if (r.getParentId() == null) refreshShowRating(r.getShowId());
    }

    @Transactional
    public void adminRestore(Long reviewId) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) throw new BusinessException(ErrorCode.REVIEW_NOT_FOUND);
        reviewMapper.updateStatus(reviewId, STATUS_NORMAL, LocalDateTime.now());
        if (r.getParentId() == null) refreshShowRating(r.getShowId());
    }

    @Transactional
    public void adminDelete(Long reviewId) {
        ShowReview r = reviewMapper.selectById(reviewId);
        if (r == null) return;
        Long showId = r.getShowId();
        Long parentId = r.getParentId();
        imageMapper.deleteByReviewId(reviewId);
        reviewMapper.deleteById(reviewId);
        if (parentId != null) {
            reviewMapper.decrReplyCount(parentId);
        } else {
            refreshShowRating(showId);
        }
    }

    public Map<String, Object> adminList(Long showId, Integer status, String keyword,
                                         Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowReview> list = reviewMapper.selectAdminList(showId, status, keyword, offset, size);
        attachImagesAndLikes(list, null);
        int total = reviewMapper.countAdminList(showId, status, keyword);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    public Map<String, Object> adminReportList(Integer status, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<ShowReviewReport> list = reportMapper.selectByStatus(status, offset, size);
        int total = reportMapper.countByStatus(status);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        return r;
    }

    @Transactional
    public void adminHandleReport(Long adminUserId, Long reportId, String action) {
        ShowReviewReport rep = reportMapper.selectById(reportId);
        if (rep == null) return;
        int newStatus = "delete".equalsIgnoreCase(action) ? 2 : 1;
        reportMapper.updateHandle(reportId, newStatus, adminUserId, LocalDateTime.now());
        if (newStatus == 2) {
            adminDelete(rep.getReviewId());
        } else {
            // 保留:把评论从 status=1(举报中) 恢复为 0
            ShowReview r = reviewMapper.selectById(rep.getReviewId());
            if (r != null && r.getStatus() != null && r.getStatus() == STATUS_REPORTED) {
                reviewMapper.updateStatus(r.getId(), STATUS_NORMAL, LocalDateTime.now());
            }
        }
    }

    // ----- 统计回写 -----

    @SuppressWarnings("unchecked")
    public void refreshShowRating(Long showId) {
        Map<String, Object> agg = reviewMapper.aggregateRating(showId);
        Show s = new Show();
        s.setId(showId);
        BigDecimal avg = BigDecimal.ZERO;
        int count = 0;
        if (agg != null) {
            Object a = agg.get("avg_rating");
            Object c = agg.get("review_count");
            if (a != null) {
                avg = new BigDecimal(a.toString()).setScale(2, RoundingMode.HALF_UP);
            }
            if (c != null) count = Integer.parseInt(c.toString());
        }
        // 直接调用一个新方法或用现有 update 也行
        // 这里通过 showMapper 直接更新冗余字段。
        // ShowMapper 已有 update(Show);但只 update 已映射字段。
        // 为避免影响其他字段,使用专用更新 SQL:
        updateShowRatingDirect(showId, avg, count);
    }

    private void updateShowRatingDirect(Long showId, BigDecimal avgRating, int reviewCount) {
        // 简化:通过 selectById + setAvgRating + setReviewCount + showMapper.update(show)
        Show exist = showMapper.selectById(showId);
        if (exist == null) return;
        exist.setAvgRating(avgRating);
        exist.setReviewCount(reviewCount);
        exist.setUpdateTime(LocalDateTime.now());
        showMapper.update(exist);
    }
}
