package com.treasurehunt.chat.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessagesInitResult {
    private String conversationId;
    private List<ChatMessage> chatMessages;
}


