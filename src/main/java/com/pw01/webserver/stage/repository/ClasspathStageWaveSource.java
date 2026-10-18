package com.pw01.webserver.stage.repository;

import com.pw01.webserver.common.master.MasterDataLoader;

/**
 * 이미지 안 리소스 master/stage-waves.json을 읽는다(로컬·시험, pw01.stage-content.source=classpath).
 * 리소스는 실행 중에 바뀌지 않으므로 ETag가 없다.
 */
public class ClasspathStageWaveSource implements StageWaveSource {

    public static final String FILE_NAME = "stage-waves.json";

    private final MasterDataLoader loader;

    public ClasspathStageWaveSource(MasterDataLoader loader) {
        this.loader = loader;
    }

    @Override
    public StageWaveFile read() {
        return new StageWaveFile(loader.readClasspath(FILE_NAME), null);
    }

    @Override
    public String describe() {
        return "classpath:master/" + FILE_NAME;
    }

}
