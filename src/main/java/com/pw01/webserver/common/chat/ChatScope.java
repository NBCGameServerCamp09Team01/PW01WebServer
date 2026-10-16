package com.pw01.webserver.common.chat;

/**
 * 채팅 범위. 전체(서버 기준)·길드 둘만 둔다(M23 10/15 결정). 길드는 길드마다의 채널이고, 한 사람은 길드 하나에만 든다.
 */
public enum ChatScope {

    /** 접속한 모든 사람. 지금은 서버에 저장하지 않는다(GlobalChatHistory) */
    GLOBAL,

    /** 그 길드원만. 채팅 모듈이 저장하고 불러온다(GuildChatHistory). 길드 소속은 소셜이 답한다(GuildDirectory) */
    GUILD

}
