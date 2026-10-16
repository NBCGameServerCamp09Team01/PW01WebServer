package com.pw01.webserver.guild.dto;

import com.pw01.webserver.stageplay.dto.StagePlayIds;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * G3 길드 만들기 요청(guild-api.md). 형식만 여기서 본다. 조건·이미 길드·이름 중복·상한은 서비스가 판정한다.
 * 이름 규칙은 V10 CHECK ck_guild_name과 같다: 한글(완성형)·영문·숫자 2~12자, 가운데 공백 가능, 앞뒤·연속 공백 불가.
 */
public record GuildCreateRequest(

        @NotBlank(message = "requestId가 필요합니다.")
        @Pattern(regexp = StagePlayIds.UUID_PATTERN, message = "requestId는 UUID(하이픈 36자)여야 합니다.")
        String requestId,

        @NotBlank(message = "name이 필요합니다.")
        @Pattern(regexp = NAME_PATTERN, message = "길드 이름은 한글·영문·숫자 2~12자여야 합니다(가운데 공백 하나씩 가능).")
        String name
) {

    /** 연속 공백 금지(앞쪽 부정 전방 탐색) + 첫·끝 글자는 공백 아님 + 2~12자 */
    public static final String NAME_PATTERN = "^(?!.* {2})[가-힣A-Za-z0-9][가-힣A-Za-z0-9 ]{0,10}[가-힣A-Za-z0-9]$";

}
