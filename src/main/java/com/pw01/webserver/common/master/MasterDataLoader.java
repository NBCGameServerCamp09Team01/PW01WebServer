package com.pw01.webserver.common.master;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * 마스터 데이터 틀(SF-7): classpath:master/{파일}을 읽어 파일 모양 record로 바꾼다.
 * 파일마다 "모양 record + 그 기능의 부품"을 두고, 부품이 생성자에서 이 로더로 읽은 뒤 자기 규칙으로 검사한다.
 * 파일이 없거나, JSON을 못 읽거나, 모르는 칸이 있으면 MasterDataException → 기동 실패.
 * Spring이 만든 JsonMapper를 쓰므로 공통 설정(모르는 칸이면 실패)이 그대로 적용된다.
 * 빠진 숫자 칸은 0으로 들어올 수 있으므로 각 파일의 검사가 범위(≥ 1 등)를 본다.
 * 값은 기동 때 한 번 읽고 바꾸지 않는다. 고치면 서버를 다시 띄운다.
 */
@Component
public class MasterDataLoader {

    private static final String DEFAULT_DIRECTORY = "master/";

    private final JsonMapper jsonMapper;
    private final String directory;

    @Autowired
    public MasterDataLoader(JsonMapper jsonMapper) {
        this(jsonMapper, DEFAULT_DIRECTORY);
    }

    /** 시험용: 다른 폴더(예: master-test/)에서 읽는다 */
    MasterDataLoader(JsonMapper jsonMapper, String directory) {
        this.jsonMapper = jsonMapper;
        this.directory = directory;
    }

    public <T> T load(String fileName, Class<T> type) {
        ClassPathResource resource = new ClassPathResource(directory + fileName);
        if (!resource.exists()) {
            throw new MasterDataException(fileName, "파일이 없습니다.");
        }
        try (InputStream in = resource.getInputStream()) {
            T value = jsonMapper.readValue(in, type);
            if (value == null) {
                throw new MasterDataException(fileName, "내용이 비어 있습니다.");
            }
            return value;
        } catch (JacksonException e) {
            throw new MasterDataException(fileName, "JSON 모양이 틀렸습니다: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new MasterDataException(fileName, "파일을 읽지 못했습니다.", e);
        }
    }

}
