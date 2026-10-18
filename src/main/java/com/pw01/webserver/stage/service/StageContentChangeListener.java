package com.pw01.webserver.stage.service;

import com.pw01.webserver.realtime.event.ServerContentChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 다른 서버가 웨이브 정의를 바꿨다는 알림(CONTENT_CHANGED)을 받아 이 서버도 다시 읽게 한다.
 * 알림은 실시간 받기 스레드에서 오므로 여기서는 다시 읽기를 예약만 하고 바로 돌아간다(StageContentSafetyPoll의 스레드가 읽음).
 * 알림을 놓쳐도 같은 스레드의 안전망 주기 확인이 따라온다.
 */
@Component
public class StageContentChangeListener {

    private final StageContentSafetyPoll background;

    public StageContentChangeListener(StageContentSafetyPoll background) {
        this.background = background;
    }

    @EventListener
    public void onContentChanged(ServerContentChangedEvent event) {
        background.reloadSoon("relay");
    }

}
