package com.ll.framework.ioc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ll.domain.testPost.testPost.repository.TestPostRepository;
import com.ll.domain.testPost.testPost.service.TestFacadePostService;
import com.ll.domain.testPost.testPost.service.TestPostService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ApplicationContextTest {
    private static ApplicationContext applicationContext;

    @BeforeAll
    public static void beforeAll() {
        applicationContext = new ApplicationContext("com.ll");
        applicationContext.init();
    }

    @Test
    @DisplayName("ApplicationContext 객체 생성")
    public void t1() {
        System.out.println(applicationContext);
    }

    @Test
    @DisplayName("testPostService 빈 얻기")
    public void t2() {
        TestPostService testPostService = applicationContext
                .genBean("testPostService");

        assertThat(testPostService).isNotNull();
    }

    @Test
    @DisplayName("testPostService 빈을 다시 얻기, 싱글톤이어야 함")
    public void t3() {
        TestPostService testPostService1 = applicationContext
                .genBean("testPostService");

        TestPostService testPostService2 = applicationContext
                .genBean("testPostService");

        assertThat(testPostService1).isNotNull();
        assertThat(testPostService2).isNotNull();
        assertThat(testPostService1).isSameAs(testPostService2);
    }

    @Test
    @DisplayName("testPostRepository")
    public void t4() {
        TestPostRepository testPostRepository = applicationContext
                .genBean("testPostRepository");

        assertThat(testPostRepository).isNotNull();
    }

    @Test
    @DisplayName("testPostService has testPostRepository")
    public void t5() {
        TestPostService testPostService = applicationContext
                .genBean("testPostService");

        assertThat(testPostService).hasFieldOrPropertyWithValue(
                "testPostRepository",
                applicationContext.genBean("testPostRepository")
        );
    }

    @Test
    @DisplayName("testFacadePostService has testPostService, testPostRepository")
    public void t6() {
        TestFacadePostService testFacadePostService = applicationContext
                .genBean("testFacadePostService");

        assertThat(testFacadePostService).hasFieldOrPropertyWithValue(
                "testPostService",
                applicationContext.genBean("testPostService")
        );

        assertThat(testFacadePostService).hasFieldOrPropertyWithValue(
                "testPostRepository",
                applicationContext.genBean("testPostRepository")
        );
    }

    @Test
    @DisplayName("@Bean, 아무런 의존관계가 없는 단순한 빈인 testBaseJavaTimeModule 를 생성")
    public void t7() {
        JavaTimeModule testBaseJavaTimeModule = applicationContext.genBean("testBaseJavaTimeModule");

        assertThat(testBaseJavaTimeModule).isNotNull();
    }

    @Test
    @DisplayName("@Bean, testBaseJavaTimeModule 빈에 의존하는 testBaseObjectMapper 빈을 생성")
    public void t8() {
        ObjectMapper testBaseObjectMapper = applicationContext.genBean("testBaseObjectMapper");

        assertThat(testBaseObjectMapper).isNotNull();
    }
}