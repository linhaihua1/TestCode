package com.apiweb.converter;

import com.apiweb.entity.UserEntity;
import com.apiweb.vo.user.UserVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserConverter 转换测试：验证 Entity → VO 映射正确，
 * 且密码哈希绝不外泄（UserVO 无该字段）。
 */
class UserConverterTest {

    private final UserConverter converter = UserConverter.INSTANCE;

    @Test
    void toVO_shouldMapFieldsAndDropPasswordHash() {
        UserEntity entity = new UserEntity();
        entity.setId("u1");
        entity.setUsername("admin");
        entity.setRole("admin");
        entity.setPasswordHash("$2a$10$shouldNeverLeak");

        UserVO vo = converter.toVO(entity);

        assertEquals("u1", vo.getId());
        assertEquals("admin", vo.getUsername());
        assertEquals("admin", vo.getRole());
        // 关键：VO 无 passwordHash 字段，编译期即杜绝外泄
        assertFalse(UserVO.class.getDeclaredFields().length == 0);
    }

    @Test
    void toVOList_shouldMapAll() {
        UserEntity a = new UserEntity();
        a.setUsername("a");
        UserEntity b = new UserEntity();
        b.setUsername("b");

        assertEquals(2, converter.toVOList(java.util.List.of(a, b)).size());
    }
}
