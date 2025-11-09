package io.github.test.controller;

import io.github.test.entity.OrderEntity;
import io.github.test.entity.UserEntity;
import io.github.test.service.AdvancedSqlService;
import io.github.test.service.BatchOperationService;
import io.github.test.service.ComplexQueryService;
import io.github.test.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 复杂测试控制器
 * 
 * <p>提供多个复杂场景的测试接口：</p>
 * <ul>
 *   <li>复杂查询测试</li>
 *   <li>批量操作测试</li>
 *   <li>事务测试</li>
 *   <li>高级SQL测试</li>
 * </ul>
 * 
 * @author hexlodev
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/complex")
public class ComplexTestController {

    @Autowired
    private ComplexQueryService complexQueryService;

    @Autowired
    private BatchOperationService batchOperationService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AdvancedSqlService advancedSqlService;

    // ==================== 复杂查询测试 ====================

    /**
     * 测试1：JOIN 查询（主数据源）
     * GET /api/complex/join/primary?userName=张三
     */
    @GetMapping("/join/primary")
    public Map<String, Object> testJoinQueryPrimary(@RequestParam String userName) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<OrderEntity> orders = complexQueryService.findOrdersByUserNameInPrimary(userName);
            result.put("success", true);
            result.put("data", orders);
            result.put("message", "JOIN查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试2：聚合查询（主数据源）
     * GET /api/complex/count/primary?userId=1
     */
    @GetMapping("/count/primary")
    public Map<String, Object> testCountQueryPrimary(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long count = complexQueryService.countOrdersByUserIdInPrimary(userId);
            result.put("success", true);
            result.put("data", count);
            result.put("message", "聚合查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试3：复杂 WHERE 条件（主数据源）
     * GET /api/complex/where/primary?name=张三&phone=13800138000
     */
    @GetMapping("/where/primary")
    public Map<String, Object> testComplexWherePrimary(
            @RequestParam String name,
            @RequestParam String phone) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = complexQueryService.findUsersByNameAndPhoneInPrimary(name, phone);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "复杂WHERE查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试4：LIKE 查询（主数据源）
     * GET /api/complex/like/primary?pattern=张
     */
    @GetMapping("/like/primary")
    public Map<String, Object> testLikeQueryPrimary(@RequestParam String pattern) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = complexQueryService.findUsersByNameLikeInPrimary(pattern);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "LIKE查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ==================== 批量操作测试 ====================

    /**
     * 测试5：批量插入（主数据源）
     * POST /api/complex/batch/insert/primary
     * Body: [{"name":"用户1","phone":"13800000001",...}, ...]
     */
    @PostMapping("/batch/insert/primary")
    public Map<String, Object> testBatchInsertPrimary(@RequestBody List<UserEntity> users) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = batchOperationService.batchInsertToPrimary(users);
            result.put("success", true);
            result.put("data", count);
            result.put("message", "批量插入成功（主数据源），插入了 " + count + " 条记录");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试6：批量插入（从数据源）
     * POST /api/complex/batch/insert/secondary
     */
    @PostMapping("/batch/insert/secondary")
    public Map<String, Object> testBatchInsertSecondary(@RequestBody List<UserEntity> users) {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = batchOperationService.batchInsertToSecondary(users);
            result.put("success", true);
            result.put("data", count);
            result.put("message", "批量插入成功（从数据源），插入了 " + count + " 条记录");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试7：批量查询（主数据源）
     * GET /api/complex/batch/select/primary?ids=1,2,3
     */
    @GetMapping("/batch/select/primary")
    public Map<String, Object> testBatchSelectPrimary(@RequestParam String ids) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Long> idList = new ArrayList<>();
            for (String id : ids.split(",")) {
                idList.add(Long.parseLong(id.trim()));
            }
            List<UserEntity> users = batchOperationService.batchSelectFromPrimary(idList);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "批量查询成功（主数据源），查询了 " + users.size() + " 条记录");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试8：创建测试数据（主数据源）
     * POST /api/complex/test-data/primary?count=10
     */
    @PostMapping("/test-data/primary")
    public Map<String, Object> createTestDataPrimary(@RequestParam(defaultValue = "10") int count) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = batchOperationService.createTestDataInPrimary(count);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "创建测试数据成功（主数据源），创建了 " + users.size() + " 条记录");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ==================== 事务测试 ====================

    /**
     * 测试9：事务操作 - 创建用户和订单（主数据源）
     * POST /api/complex/transaction/primary
     * Body: {"user":{...}, "order":{...}}
     */
    @PostMapping("/transaction/primary")
    @SuppressWarnings("unchecked")
    public Map<String, Object> testTransactionPrimary(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 简化处理：从 request 中提取 user 和 order
            // 实际场景中应该使用 DTO 对象
            UserEntity user = new UserEntity();
            Map<String, Object> userMap = (Map<String, Object>) request.get("user");
            user.setName((String) userMap.get("name"));
            user.setPhone((String) userMap.get("phone"));
            user.setAge((Integer) userMap.get("age"));
            user.setEmail((String) userMap.get("email"));

            OrderEntity order = new OrderEntity();
            Map<String, Object> orderMap = (Map<String, Object>) request.get("order");
            order.setOrderNo((String) orderMap.get("orderNo"));
            order.setAmount((String) orderMap.get("amount"));
            order.setAddress((String) orderMap.get("address"));
            order.setStatus((String) orderMap.get("status"));

            transactionService.createUserAndOrderInPrimary(user, order);
            result.put("success", true);
            result.put("message", "事务操作成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试10：跨数据源操作
     * POST /api/complex/cross-datasource
     * Body: {"name":"张三","phone":"13800138000",...}
     */
    @PostMapping("/cross-datasource")
    public Map<String, Object> testCrossDatasource(@RequestBody UserEntity user) {
        Map<String, Object> result = new HashMap<>();
        try {
            transactionService.createUserInMultipleDatasources(user);
            result.put("success", true);
            result.put("message", "跨数据源操作成功，已在三个数据源中创建用户");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ==================== 高级SQL测试 ====================

    /**
     * 测试11：ORDER BY 查询（主数据源）
     * GET /api/complex/order-by/primary
     */
    @GetMapping("/order-by/primary")
    public Map<String, Object> testOrderByPrimary() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = advancedSqlService.findUsersOrderByNameInPrimary();
            result.put("success", true);
            result.put("data", users);
            result.put("message", "ORDER BY查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试12：分页查询（主数据源）
     * GET /api/complex/page/primary?pageNum=1&pageSize=10
     */
    @GetMapping("/page/primary")
    public Map<String, Object> testPaginationPrimary(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = advancedSqlService.findUsersWithPaginationInPrimary(pageNum, pageSize);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "分页查询成功（主数据源），第 " + pageNum + " 页，每页 " + pageSize + " 条");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 测试13：多条件查询（主数据源）
     * GET /api/complex/multi-condition/primary?name=张三&phone=13800138000&minAge=20
     */
    @GetMapping("/multi-condition/primary")
    public Map<String, Object> testMultiConditionPrimary(
            @RequestParam String name,
            @RequestParam String phone,
            @RequestParam Integer minAge) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<UserEntity> users = advancedSqlService.findUsersWithComplexConditionsInPrimary(name, phone, minAge);
            result.put("success", true);
            result.put("data", users);
            result.put("message", "多条件查询成功（主数据源）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}

