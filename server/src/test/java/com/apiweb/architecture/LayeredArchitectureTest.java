package com.apiweb.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * 分层架构约束测试（阶段 2）。
 *
 * <p>约束目标：确保 Web 层、业务层、持久层之间的依赖方向单向向下，
 * 杜绝 Controller 直接操作 Mapper、Mapper 回依赖上层等架构腐化问题。
 *
 * <p>分层定义：
 * <ul>
 *   <li><b>Web 层</b>：{@code ..controller..}（HTTP 入口，参数校验 + 调 Service）</li>
 *   <li><b>业务层</b>：{@code ..service..}（业务逻辑 + 事务下沉）</li>
 *   <li><b>持久层</b>：{@code ..mapper..}（MyBatis-Plus 数据访问接口）</li>
 * </ul>
 *
 * <p>说明：{@code report}、{@code engine}、{@code openapi} 等为领域/基础设施包，
 * 不在本严格分层约束之内（它们与 service 层协作，属于同层或下层）。
 */
public class LayeredArchitectureTest {

    private static JavaClasses classes;

    /**
     * 一次性导入全部主代码字节码（仅导入 com.apiweb 包，排除测试自身）。
     */
    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.apiweb");
    }

    /**
     * 严格分层规则：controller → service → mapper，依赖方向只能向下。
     */
    static final ArchRule layered = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Web").definedBy("..controller..")
            .layer("Service").definedBy("..service..")
            .layer("Persistence").definedBy("..mapper..")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Service").mayOnlyBeAccessedByLayers("Web")
            .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service");

    @Test
    void 分层依赖方向必须单向向下() {
        layered.check(classes);
    }

    @Test
    void controller不得依赖mapper() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..mapper..")
                .check(classes);
    }

    @Test
    void mapper不得回依赖service或controller() {
        noClasses().that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..controller..")
                .check(classes);
    }

    @Test
    void service不得回依赖controller() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..controller..")
                .check(classes);
    }

    @Test
    void controller不得依赖converter() {
        // Converter 属 Service 层内部的编译期转换组件，表现层不应直接使用
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..converter..")
                .check(classes);
    }

    @Test
    void entity不得依赖任何上层包() {
        // 实体是纯数据模型，不应依赖 service/controller/converter
        noClasses().that().resideInAPackage("..entity..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..controller..", "..converter..")
                .check(classes);
    }

    @Test
    void service只能注入mapper与converter不直接依赖spring_mvc() {
        // Service 不应依赖 Spring Web MVC（如 HttpServletRequest、ResponseEntity 等）
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                .check(classes);
    }
}
