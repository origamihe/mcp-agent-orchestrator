package com.mcp.common.tts;

import reactor.core.publisher.Mono;

/**
 * TTS 语音合成服务接口
 */
public interface TtsService {

    /**
     * 将文本转为语音文件
     * @param text 要合成的文本
     * @param voiceId 音色ID（如 "澪音"）
     * @return 语音文件的本地路径
     */
    Mono<String> synthesize(String text, String voiceId);

    /**
     * 将文本转为语音字节数组（用于直接发送）
     * @param text 要合成的文本
     * @param voiceId 音色ID
     * @return 语音文件二进制数据
     */
    Mono<byte[]> synthesizeToBytes(String text, String voiceId);

    /**
     * 健康检查
     */
    Mono<Boolean> healthCheck();
}