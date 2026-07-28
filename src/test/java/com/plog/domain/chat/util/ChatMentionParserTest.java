package com.plog.domain.chat.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ChatMentionParserTest {

    @Test
    void 여러_명_멘션을_모두_추출한다() {
        Set<String> result = ChatMentionParser.extractNicknameCandidates("@지현 @민수 확인 부탁드려요");
        assertThat(result).containsExactly("지현", "민수");
    }

    @Test
    void 동일_닉네임이_여러_번_등장해도_중복_제거된다() {
        Set<String> result = ChatMentionParser.extractNicknameCandidates("@지현 @지현 두 번 불러봄");
        assertThat(result).containsExactly("지현");
    }

    @Test
    void 문장부호가_붙은_닉네임에서_문장부호를_제거한다() {
        Set<String> result = ChatMentionParser.extractNicknameCandidates("@지현, 이거 봐주세요. @민수!");
        assertThat(result).containsExactlyInAnyOrder("지현", "민수");
    }

    @Test
    void 골뱅이만_입력된_경우_무시한다() {
        Set<String> result = ChatMentionParser.extractNicknameCandidates("@ 안녕하세요");
        assertThat(result).isEmpty();
    }

    @Test
    void 멘션이_없으면_빈_집합을_반환한다() {
        assertThat(ChatMentionParser.extractNicknameCandidates("그냥 일반 메시지입니다")).isEmpty();
    }

    @Test
    void 빈_문자열이나_null이면_빈_집합을_반환한다() {
        assertThat(ChatMentionParser.extractNicknameCandidates(null)).isEmpty();
        assertThat(ChatMentionParser.extractNicknameCandidates("")).isEmpty();
    }
}