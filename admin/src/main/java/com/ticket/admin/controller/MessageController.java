package com.ticket.admin.controller;

import com.ticket.admin.dto.MessageBroadcastRequest;
import com.ticket.admin.dto.MessageListRequest;
import com.ticket.admin.dto.MessageSendRequest;
import com.ticket.common.result.Result;
import com.ticket.core.domain.entity.Message;
import com.ticket.core.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Tag(name = "消息管理", description = "群发 / 单发 / 已发消息列表")
@RestController
@RequestMapping("/api/admin/message")
public class MessageController {

    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @Operation(summary = "群发系统通知")
    @PostMapping("/broadcast")
    public Result<Message> broadcast(@Valid @RequestBody MessageBroadcastRequest req) {
        return Result.success(service.broadcast(req.getType(), req.getTitle(), req.getContent(),
                req.getLinkType(), req.getLinkTarget()));
    }

    @Operation(summary = "单发/批量发送")
    @PostMapping("/send")
    public Result<Message> send(@Valid @RequestBody MessageSendRequest req) {
        return Result.success(service.sendToUsers(req.getUserIds(), req.getType(),
                req.getTitle(), req.getContent(), req.getLinkType(), req.getLinkTarget()));
    }

    @Operation(summary = "已发消息列表")
    @PostMapping("/list")
    public Result<Map<String, Object>> list(@Valid @RequestBody MessageListRequest req) {
        return Result.success(service.listAdmin(req.getType(), req.getBroadcast(), req.getPage(), req.getSize()));
    }
}
