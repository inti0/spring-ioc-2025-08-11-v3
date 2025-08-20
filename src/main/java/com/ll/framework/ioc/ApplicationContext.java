package com.ll.framework.ioc;

import com.ll.framework.ioc.annotations.Bean;
import com.ll.framework.ioc.annotations.Component;
import com.ll.standard.util.Ut;
import com.ll.standard.util.Ut.topologicalSort;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.reflections.Reflections;

public class ApplicationContext {
    private final Map<String, Object> beans = new HashMap<>();
    private final Map<String, beanCreateStrategy> beanNameStrategyMap = new HashMap<>();
    private final List<String> beanGraph = new ArrayList<>();

    // 빈을 생성자 혹은 메소드로 생성하는 클래스
    // 사용하지 않는 쪽은 null로 받는다
    private class beanCreateStrategy {
        Constructor<?> constructor;
        Method method;

        public beanCreateStrategy(Constructor<?> constructor, Method method) {
            this.constructor = constructor;
            this.method = method;
        }

        Object registerBean() throws InvocationTargetException, InstantiationException, IllegalAccessException {
            if (constructor != null) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                Object[] parameterInstance = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    String beanName = findBeanNameForType(parameterType);
                    parameterInstance[i] = beans.get(beanName);
                }
                return constructor.newInstance(parameterInstance);
            }
            if (method != null) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                Object[] parameterInstance = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    String beanName = findBeanNameForType(parameterType);
                    parameterInstance[i] = beans.get(beanName);
                }
                Object declaredObject = beans.get(getBeanName(method.getDeclaringClass()));
                return method.invoke(declaredObject, parameterInstance);
            }
            return null;
        }
    }

    public ApplicationContext(String basePackage) {
        try {
            scanWithAnnotation(basePackage);
            Queue<String> sortedBeanNames = sortBeanByDependency();

            System.out.println("의존성 그래프: " + beanGraph);
            System.out.println("Bean 생성 순서: " + sortedBeanNames);

            while (!sortedBeanNames.isEmpty()) {
                String beanName = sortedBeanNames.poll();
                System.out.println("생성 중인 Bean: " + beanName);

                beanCreateStrategy beanCreateStrategy = beanNameStrategyMap.get(beanName);
                Object instance = beanCreateStrategy.registerBean();
                System.out.println("생성 완료: " + beanName + " -> " + instance);

                beans.put(beanName, instance);
            }
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // 빈 등록 대상을 어노테이션을 통해 찾는다
    private void scanWithAnnotation(String basePackage) throws NoSuchMethodException {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> scannedClass = reflections.getTypesAnnotatedWith(Component.class);
        scannedClass.removeIf(Class::isAnnotation);
        for (Class<?> clazz : scannedClass) {
            scanComponentTagged(clazz);
            for (Method method : clazz.getDeclaredMethods()) {
                scanBeanTagged(method);
            }
        }
    }

    // 의존성 관계에 따라 beanName을 정렬하여 반환
    private Queue<String> sortBeanByDependency() {
        Queue<String> sortedBeanNames = topologicalSort.sort(beanGraph);
        if (sortedBeanNames.size() < beanNameStrategyMap.size()) {
            throw new RuntimeException("순환 참조 감지");
        }
        return sortedBeanNames;
    }

    // @Component이 선언된 클래스를 beanNameStrategyMap 에 생성자와 함께 등록하고,
    // beanGraph에 의존성 관계 등록
    private void scanComponentTagged(Class<?> clazz) {
        String beanName = getBeanName(clazz);
        Constructor<?> targetConstructor = getTargetConstructor(clazz);
        beanNameStrategyMap.put(beanName, new beanCreateStrategy(targetConstructor, null));
        Class<?>[] parameterTypes = targetConstructor.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
            String node = getBeanName(parameterType) + " " + beanName;
            beanGraph.add(node);
        }
    }

    // @Bean이 선언된 메소드를 beanNameStrategyMap 에 메소드와 함께 등록하고,
    // beanGraph에 의존성 관계 등록
    private void scanBeanTagged(Method method) {
        if (!method.isAnnotationPresent(Bean.class)) {
            return;
        }

        String methodName = method.getName();
        beanNameStrategyMap.put(methodName, new beanCreateStrategy(null, method));

        Class<?>[] parameterTypes = method.getParameterTypes();
        for (Class<?> parameterType : parameterTypes) {
            String node = findBeanNameForType(parameterType) + " " + methodName;
            beanGraph.add(node);
        }
        String declaredClassNode = getBeanName(method.getDeclaringClass()) + " " + methodName;
        beanGraph.add(declaredClassNode);
    }

    // 생성자 주입의 bean 이름은 클래스명에서 첫 글자를 소문자로 변환하여 사용한다
    private String getBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Ut.str.lcfirst(simpleName);
    }

    // 타입으로 빈 이름을 찾는 메소드
    private String findBeanNameForType(Class<?> type) {
        // 1. @Bean 메소드 중에서 해당 타입을 반환하는 메소드 찾기
        for (Map.Entry<String, beanCreateStrategy> entry : beanNameStrategyMap.entrySet()) {
            beanCreateStrategy target = entry.getValue();
            if (target.method != null && target.method.getReturnType().equals(type)) {
                return entry.getKey(); // 메소드 이름 반환
            }
        }

        // 2. @Component로 등록된 빈 찾기
        return getBeanName(type);
    }

    // 단일 생성자만 지원 : 현재 시나리오에서는 @Autowired가 존재하지 않음
    private Constructor<?> getTargetConstructor(Class<?> clazz) {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        if (declaredConstructors.length == 1) {
            return declaredConstructors[0];
            throw new RuntimeException("생성자가 없거나 2개 이상 : " + clazz.getName());
        }
    }


    public void init() {
    }

    public <T> T genBean(String beanName) {
        return (T) beans.getOrDefault(beanName, null);
    }
}
