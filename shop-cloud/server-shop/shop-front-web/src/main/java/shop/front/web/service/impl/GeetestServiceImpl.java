package shop.front.web.service.impl;

import cn.miven.cloud.common.jedis.JedisClient;
import cn.miven.cloud.common.jedis.JedisClientPool;
import cn.miven.cloud.common.util.StringEncodeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import shop.front.web.manager.consumer.GeetestConsumer;
import shop.front.web.pojo.Geetest;
import shop.front.web.pojo.GeetestInit;
import shop.front.web.service.GeetestService;

import java.util.Map;
import java.util.UUID;

/**
 * 极验
 *
 * @author mingzhi.xie
 * @date 2019/4/4
 */

@Service
public class GeetestServiceImpl implements GeetestService {

    private final static Logger logger = LoggerFactory.getLogger(GeetestServiceImpl.class);

    private final static int CHALLENGE_LENGTH = 32;

    /**
     * 公钥
     */
    @Value("geetest.captchaId")
    private String captchaId;

    /**
     * 私钥
     */
    @Value("geetest.privateKey")
    private String privateKey;

    /**
     * 是否开启新的failBack
     */
    @Value("geetest.newFailBack")
    private boolean newFailBack;

    private JedisClient jedisClient = new JedisClientPool();

    private final GeetestConsumer geetestConsumer;

    public GeetestServiceImpl(GeetestConsumer geetestConsumer) {
        this.geetestConsumer = geetestConsumer;
    }

    @Override
    public GeetestInit geetestInit(Map<String, String> map) {
        return registerChallenge(map);
    }



    /**
     * 用captchaID进行注册，更新challenge
     * @return 1表示注册成功，0表示注册失败
     */
    private GeetestInit registerChallenge(Map<String, String> param) {
        param.put("gt", this.captchaId);
        param.put("json_format", JSON_FORMAT);

        ResponseEntity<Geetest> register = geetestConsumer.register(param);
        if (register.getStatusCode() != HttpStatus.OK) {
            logger.error("Geetest server register failed, error http status code: {}", register.getStatusCode());
            return registerFail();
        }

        String challenge;
        if (register.getBody() != null && register.getBody().getChallenge().length() == CHALLENGE_LENGTH) {
            challenge = register.getBody().getChallenge();
            StringEncodeUtils.md5Encode(challenge + this.privateKey);
            return registerOk(challenge);
        } else {
            logger.error("Geetest server register failed, challenge length is not 32");
            return registerFail();
        }
    }

    /**
     * 注册成功
     */
    private GeetestInit registerOk(String challenge) {
        String key = UUID.randomUUID().toString();
        jedisClient.set(key, "1");
        jedisClient.expire(key,360);

        GeetestInit geetestInit = new GeetestInit();
        geetestInit.setSuccess(1);
        geetestInit.setChallenge(challenge);
        geetestInit.setStatusKey(key);

        return geetestInit;
    }

    /**
     * 注册失败
     */
    private GeetestInit registerFail() {
        String md5Str1 = StringEncodeUtils.md5Encode(String.valueOf(Math.round(Math.random() * 100)));
        String md5Str2 = StringEncodeUtils.md5Encode(String.valueOf(Math.round(Math.random() * 100)));
        String challenge = md5Str1 + md5Str2.substring(0, 2);

        GeetestInit geetestInit = new GeetestInit();
        geetestInit.setSuccess(0);
        geetestInit.setChallenge(challenge);
        geetestInit.setGt(this.captchaId);
        geetestInit.setNewCaptcha(this.newFailBack);

        return geetestInit;

    }
}
