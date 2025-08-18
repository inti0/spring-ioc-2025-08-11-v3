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

    private class beanCreateStrategy {
        Constructor<?> constructorStrategy;
        Method methodStrategy;

        public beanCreateStrategy(Constructor<?> constructorStrategy, Method methodStrategy) {
            this.constructorStrategy = constructorStrategy;
            this.methodStrategy = methodStrategy;
        }

        Object registerBean() throws InvocationTargetException, InstantiationException, IllegalAccessException {
            if (constructorStrategy != null) {
                Class<?>[] parameterTypes = constructorStrategy.getParameterTypes();
                Object[] parameterInstance = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    String beanName = findBeanNameForType(parameterType);
                    parameterInstance[i] = beans.get(beanName);
                }
                return constructorStrategy.newInstance(parameterInstance);
            }
            if (methodStrategy != null) {
                Class<?>[] parameterTypes = methodStrategy.getParameterTypes();
                Object[] parameterInstance = new Object[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    Class<?> parameterType = parameterTypes[i];
                    String beanName = findBeanNameForType(parameterType);
                    parameterInstance[i] = beans.get(beanName);
                }
                Object declaredObject = beans.get(getBeanName(methodStrategy.getDeclaringClass()));
                return methodStrategy.invoke(declaredObject, parameterInstance);
            }
            return null;
        }
    }

    public ApplicationContext(String basePackage) {
        try {
            scanWithAnnotation(basePackage);
            Queue<String> sortedBeenNames = topologicalSort.sort(beanGraph);
            if (sortedBeenNames.size() < beanNameStrategyMap.size()) {
                throw new RuntimeException("순환 참조 감지");
            }
            System.out.println("의존성 그래프: " + beanGraph);
            System.out.println("Bean 생성 순서: " + sortedBeenNames);

            while (!sortedBeenNames.isEmpty()) {
                String beanName = sortedBeenNames.poll();
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

    // @Component 클래스 : 빈 이름 - 빈생성 전략 맵,의존성 탐색 그래프 등록
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

    // @Bean 메소드 : 빈 이름 - 빈생성 전략 맵,의존성 탐색 그래프 등록
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
            if (target.methodStrategy != null && target.methodStrategy.getReturnType().equals(type)) {
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
//        } else {
//            for (Constructor<?> constructor : declaredConstructors) {
//                if (constructor.isAnnotationPresent(AutoWired.class)) {
//                    return constructor;
//                }
//            }
        }
        throw new RuntimeException("생성자가 2개 이상 : " + clazz.getName());
    }


    public void init() {
    }

    public <T> T genBean(String beanName) {
        return (T) beans.getOrDefault(beanName, null);
    }
}
