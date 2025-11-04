//package io.github.hexlodev.core.cache;
//
//import io.github.hexlodev.core.strategy.FieldEncryptorStrategy;
//
//
///**
// * 加密策略实例缓存测试类
// *
// * @author hexlodev
// * @since 1.0.0
// */
//public class StrategyCacheTest {
//
//    /**
//     * 测试用的简单加密策略实现
//     */
//    private static class TestStrategy implements FieldEncryptorStrategy {
//        private final String id;
//
//        public TestStrategy() {
//            this.id = "default";
//        }
//
//        public TestStrategy(String id) {
//            this.id = id;
//        }
//
//        @Override
//        public String encryption(String oldValue) {
//            return oldValue != null ? "(加密:" + id + ")" + oldValue : null;
//        }
//
//        @Override
//        public String decryption(String oldValue) {
//            if (oldValue != null) {
//                String prefix = "(加密:" + id + ")";
//                if (oldValue.startsWith(prefix)) {
//                    return oldValue.substring(prefix.length());
//                }
//            }
//            return oldValue;
//        }
//    }
//
//    @BeforeEach
//    void setUp() {
//        // 每个测试前清空缓存
//        StrategyCache.clear();
//    }
//
//    @Test
//    void testGetStrategy_WithManualRegistration() {
//        // 手动注册策略实例
//        TestStrategy testStrategy = new TestStrategy("test1");
//        StrategyCache.registerStrategy(TestStrategy.class, testStrategy);
//
//        // 获取策略实例，应该返回缓存的实例
//        FieldEncryptorStrategy strategy1 = StrategyCache.getStrategy(TestStrategy.class);
//        FieldEncryptorStrategy strategy2 = StrategyCache.getStrategy(TestStrategy.class);
//
//        assertNotNull(strategy1);
//        assertNotNull(strategy2);
//        // 应该返回同一个实例（单例）
//        assertSame(strategy1, strategy2);
//        assertSame(testStrategy, strategy1);
//
//        // 验证功能
//        String encrypted = strategy1.encryption("test");
//        assertEquals("(加密:test1)test", encrypted);
//        String decrypted = strategy1.decryption(encrypted);
//        assertEquals("test", decrypted);
//    }
//
//    @Test
//    void testGetStrategy_DirectInstantiation() {
//        // 不手动注册，直接获取（在非 Spring 环境下会尝试直接实例化）
//        FieldEncryptorStrategy strategy1 = StrategyCache.getStrategy(TestStrategy.class);
//        FieldEncryptorStrategy strategy2 = StrategyCache.getStrategy(TestStrategy.class);
//
//        assertNotNull(strategy1);
//        assertNotNull(strategy2);
//        // 应该返回同一个实例（缓存）
//        assertSame(strategy1, strategy2);
//
//        // 验证功能
//        String encrypted = strategy1.encryption("test");
//        assertNotNull(encrypted);
//        assertTrue(encrypted.contains("test"));
//    }
//
//    @Test
//    void testGetStrategy_NullParameter() {
//        // 测试 null 参数应该抛出异常
//        assertThrows(IllegalArgumentException.class, () -> {
//            StrategyCache.getStrategy(null);
//        });
//    }
//
//    @Test
//    void testRegisterStrategy_NullParameters() {
//        // 测试注册时参数为 null
//        assertThrows(IllegalArgumentException.class, () -> {
//            StrategyCache.registerStrategy(null, new TestStrategy());
//        });
//
//        assertThrows(IllegalArgumentException.class, () -> {
//            StrategyCache.registerStrategy(TestStrategy.class, null);
//        });
//    }
//
//    @Test
//    void testClear() {
//        // 注册几个策略
//        StrategyCache.registerStrategy(TestStrategy.class, new TestStrategy("test1"));
//
//        assertEquals(1, StrategyCache.size());
//        assertTrue(StrategyCache.contains(TestStrategy.class));
//
//        // 清空缓存
//        StrategyCache.clear();
//
//        assertEquals(0, StrategyCache.size());
//        assertFalse(StrategyCache.contains(TestStrategy.class));
//    }
//
//    @Test
//    void testSize() {
//        assertEquals(0, StrategyCache.size());
//
//        StrategyCache.registerStrategy(TestStrategy.class, new TestStrategy());
//        assertEquals(1, StrategyCache.size());
//
//        // 再次获取不应该增加大小
//        StrategyCache.getStrategy(TestStrategy.class);
//        assertEquals(1, StrategyCache.size());
//    }
//
//    @Test
//    void testContains() {
//        assertFalse(StrategyCache.contains(TestStrategy.class));
//
//        StrategyCache.registerStrategy(TestStrategy.class, new TestStrategy());
//        assertTrue(StrategyCache.contains(TestStrategy.class));
//    }
//
//    @Test
//    void testRemove() {
//        TestStrategy testStrategy = new TestStrategy("test1");
//        StrategyCache.registerStrategy(TestStrategy.class, testStrategy);
//
//        assertTrue(StrategyCache.contains(TestStrategy.class));
//
//        // 移除
//        FieldEncryptorStrategy removed = StrategyCache.remove(TestStrategy.class);
//        assertNotNull(removed);
//        assertSame(testStrategy, removed);
//        assertFalse(StrategyCache.contains(TestStrategy.class));
//
//        // 移除不存在的策略应该返回 null
//        FieldEncryptorStrategy removed2 = StrategyCache.remove(TestStrategy.class);
//        assertNull(removed2);
//    }
//
//    @Test
//    void testRemove_NullParameter() {
//        // null 参数应该返回 null，不抛出异常
//        FieldEncryptorStrategy removed = StrategyCache.remove(null);
//        assertNull(removed);
//    }
//
//    @Test
//    void testCacheConsistency() {
//        // 多次获取应该返回同一个实例
//        FieldEncryptorStrategy strategy1 = StrategyCache.getStrategy(TestStrategy.class);
//        FieldEncryptorStrategy strategy2 = StrategyCache.getStrategy(TestStrategy.class);
//        FieldEncryptorStrategy strategy3 = StrategyCache.getStrategy(TestStrategy.class);
//
//        assertSame(strategy1, strategy2);
//        assertSame(strategy2, strategy3);
//    }
//
//    /**
//     * 另一个测试策略类型
//     */
//    private static class AnotherStrategy implements FieldEncryptorStrategy {
//        @Override
//        public String encryption(String oldValue) {
//            return "ENC:" + oldValue;
//        }
//
//        @Override
//        public String decryption(String oldValue) {
//            return oldValue != null && oldValue.startsWith("ENC:")
//                ? oldValue.substring(4) : oldValue;
//        }
//    }
//
//    @Test
//    void testMultipleStrategyTypes() {
//        // 测试多个不同的策略类型可以同时缓存
//        StrategyCache.registerStrategy(TestStrategy.class, new TestStrategy("test1"));
//        StrategyCache.registerStrategy(AnotherStrategy.class, new AnotherStrategy());
//
//        assertEquals(2, StrategyCache.size());
//        assertTrue(StrategyCache.contains(TestStrategy.class));
//        assertTrue(StrategyCache.contains(AnotherStrategy.class));
//
//        // 验证两个策略实例不同
//        FieldEncryptorStrategy strategy1 = StrategyCache.getStrategy(TestStrategy.class);
//        FieldEncryptorStrategy strategy2 = StrategyCache.getStrategy(AnotherStrategy.class);
//        assertNotSame(strategy1, strategy2);
//    }
//
//    @Test
//    void testRegisterStrategy_Overwrite() {
//        // 测试注册可以覆盖已有策略
//        TestStrategy strategy1 = new TestStrategy("test1");
//        TestStrategy strategy2 = new TestStrategy("test2");
//
//        StrategyCache.registerStrategy(TestStrategy.class, strategy1);
//        assertSame(strategy1, StrategyCache.getStrategy(TestStrategy.class));
//
//        // 覆盖注册
//        StrategyCache.registerStrategy(TestStrategy.class, strategy2);
//        assertSame(strategy2, StrategyCache.getStrategy(TestStrategy.class));
//        assertNotSame(strategy1, StrategyCache.getStrategy(TestStrategy.class));
//
//        assertEquals(1, StrategyCache.size());
//    }
//}
//
