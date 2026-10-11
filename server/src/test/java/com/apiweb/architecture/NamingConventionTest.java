package com.apiweb.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 命名与注解约定测试（阶段 2）。
 *
 * <p>约束目标：保证各层类的命名、所在包与职责注解三者一致，
 * 提升代码可读性与团队协作一致性。
 *
 * <p>边界说明：
 * <ul>
 *   <li>{@code engine}/{@code openapi}/{@code report}/{@code mq}/{@code job} 为领域/基础设施包，
 *       其中的 {@code @Service}（如 {@code TaskExecutorService}、{@code SwaggerImporter}）
 *       属领域协作组件，不纳入「service 必须位于 service 包」约束。</li>
 *   <li>MapStruct 编译期生成的 {@code *ConverterImpl}、测试类 {@code *Test}、
 *       常量类（如 {@code OssKeys}）与 Controller 内部请求类均排除。</li>
 * </ul>
 */
public class NamingConventionTest {

    private static JavaClasses classes;

    /**
     * 一次性导入全部代码字节码（含测试类，规则内部再排除测试类）。
     */
    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPackages("com.apiweb");
    }

    @Test
    void controller必须位于controller包() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..controller..")
                .check(classes);
    }

    @Test
    void controller类名必须以Controller结尾() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller")
                .check(classes);
    }

    @Test
    void controller包中所有顶层类必须以Controller结尾() {
        classes().that().resideInAPackage("..controller..")
                .and().areTopLevelClasses()
                .should().haveSimpleNameEndingWith("Controller")
                .check(classes);
    }

    @Test
    void service包中的Service类必须以Service结尾() {
        classes().that().resideInAPackage("..service..")
                .and().areAnnotatedWith(Service.class)
                .should().haveSimpleNameEndingWith("Service")
                .check(classes);
    }

    @Test
    void mapper接口必须位于mapper包() {
        classes().that().areAnnotatedWith(org.apache.ibatis.annotations.Mapper.class)
                .should().resideInAPackage("..mapper..")
                .check(classes);
    }

    @Test
    void mapper接口名必须以Mapper结尾() {
        classes().that().areAnnotatedWith(org.apache.ibatis.annotations.Mapper.class)
                .should().haveSimpleNameEndingWith("Mapper")
                .check(classes);
    }

    @Test
    void converter接口必须以Converter结尾且排除MapStruct实现类() {
        classes().that().resideInAPackage("..converter..")
                .and().areInterfaces()
                .should().haveSimpleNameEndingWith("Converter")
                .check(classes);
    }

    @Test
    void 顶层DTO必须以Req或Request结尾且位于dto包() {
        classes().that().areTopLevelClasses()
                .and().haveSimpleNameEndingWith("Req")
                .or().areTopLevelClasses().and().haveSimpleNameEndingWith("Request")
                .should().resideInAPackage("..dto..")
                .check(classes);
    }

    @Test
    void 顶层VO必须以VO结尾且位于vo包() {
        classes().that().areTopLevelClasses()
                .and().haveSimpleNameEndingWith("VO")
                .should().resideInAPackage("..vo..")
                .check(classes);
    }
}
