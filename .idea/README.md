## 📚 지식 습득
### 프록시 패턴

```java
interface SomeService {
    void service();
}

class ProxyObject implements SomeService {
    private final SomeService someService;

    public ProxyObject(SomeService someService) {
        this.someService = someService;
    }

    @Override
    public void service() {
        someService.service();
    }
}

class RealObject implements SomeService {

    @Override
    public void service(){
        System.out.println("do Something");
    };
}

class Client {

    void run() {
        SomeService service = new ProxyObject(new RealObject());
        service.service();
    }
}

```


<details>
<summary>ProxyFactory를 이용한 동적 프록시 구현 시도 -> 원하는 대로 구현 성공하고 동작 확인 했으나, t5, t6 통과 못함

(프록시를 통한구현 방식 자체가 의존성 주입에 성공하였더라도 t5, t6 테스트 통과에 적절하지 못하였음)</summary>

```java
public class ApplicationContext {
    private final Map<String, Object> beans;

    public ApplicationContext(String basePackage) {
        beans = new HashMap<>();
        Set<Class<?>> scannedClass = scanWithComponentAnnotation(basePackage);
        putInstanceInBeans(scannedClass);
    }

    //TODO : 순환참조 문제 고려
    private void putInstanceInBeans(Set<Class<?>> scannedClass) {
        for (Class<?> clazz : scannedClass) {
            try {
                String beanName = getBeanName(clazz);
                Constructor<?> constructor = getTargetConstructor(clazz);
                Object instance = createProxyInstance(clazz, constructor);
                System.out.println("빈 등록" + beanName);
                beans.put(beanName, instance);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Set<Class<?>> scanWithComponentAnnotation(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> scannedClass = reflections.getTypesAnnotatedWith(Component.class);
        scannedClass.removeIf(Class::isAnnotation);
        return scannedClass;
    }

    private String getBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Ut.str.lcfirst(simpleName);
    }

    //적절한 생성자 찾기 로직 생략
    private Constructor<?> getTargetConstructor(Class<?> clazz) throws NoSuchMethodException {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        if (declaredConstructors.length == 1) {
            return declaredConstructors[0];
        } else {
            throw new RuntimeException("생성자가 2개 이상" + clazz.getName());
        }
    }

    private Object createProxyInstance(Class<?> clazz, Constructor<?> constructor)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Proxy proxyInstance = createProxyInstanceWithNullArgs(clazz, constructor);
        proxyInstance.setHandler(new LazyBeanHandler(clazz));

        return proxyInstance;
    }

    private Proxy createProxyInstanceWithNullArgs(Class<?> clazz, Constructor<?> constructor)
            throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setSuperclass(clazz);
        Class<?> proxy = proxyFactory.createClass();
        Constructor<?> proxyConstructor = proxy.getDeclaredConstructor(parameterTypes);
        Object[] nullArgs = new Object[parameterTypes.length];

        Object instance = proxyConstructor.newInstance(nullArgs);
        return (Proxy) instance;
    }

    private class LazyBeanHandler implements MethodHandler {
        private final Class<?> targetClass;
        private Object realTarget;

        public LazyBeanHandler(Class<?> targetClass) {
            this.targetClass = targetClass;
        }

        @Override
        public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable {
            System.out.println("invoke 호출");
            if (realTarget == null) {
                realTarget = createRealInstance(targetClass);

                //프록시 객체 필드 동기화
                for (Field field : targetClass.getDeclaredFields()) {
                    field.setAccessible(true);
                    Object value = field.get(realTarget);
                    field.set(self, value);
                }
            }
            return method.invoke(realTarget, args);
        }
    }

    private Object createRealInstance(Class<?> clazz) throws Exception {
        System.out.println("진짜객체 생성" + clazz.getSimpleName());
        Constructor<?> constructor = getTargetConstructor(clazz);
        Class<?>[] paramTypes = constructor.getParameterTypes();

        Object[] dependencies = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            String dependencyName = getBeanName(paramTypes[i]);
            dependencies[i] = beans.get(dependencyName);

            if (dependencies[i] == null) {
                throw new RuntimeException("의존성 객체 없음 : " + paramTypes[i].getName());
            }
        }
        return constructor.newInstance(dependencies);
    }

    public void init() {
    }

    public <T> T genBean(String beanName) {
        return (T) beans.getOrDefault(beanName, null);
    }
}
```
</details>

### 위상 정렬

`Ut.topologicalSort.sort()` 에서 구현하였음.

위상 정렬을 이용해 bean을 의존성이 작은 bean부터 큰 bean으로 정렬할 수 있었다.

이를 통해 위상 정렬을 통한 의존성 파악한 후

빈을 순차적으로 생성만 하면 되도록 구현할 수 있었다.

또한, 위상 정렬을 이용하면 순환 참조를 감지할 수 있다.

--- 

<details>
<summary> t1 ~ t8 통과하는 코드 작성 (위상정렬 적용 및 리팩토링 적용 전) (tdd green) </summary>

```java
public class ApplicationContext {
    private final Map<String, Object> beans;

    public ApplicationContext(String basePackage) {
        beans = new HashMap<>();
        Set<Class<?>> scannedClass = scanWithComponentAnnotation(basePackage);
        registerComponentClass(scannedClass);
        registerBeanMethods(scannedClass);
    }

    private Set<Class<?>> scanWithComponentAnnotation(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        Set<Class<?>> scannedClass = reflections.getTypesAnnotatedWith(Component.class);
        scannedClass.removeIf(Class::isAnnotation);
        return scannedClass;
    }

    private void registerComponentClass(Set<Class<?>> scannedClass) {
        for (Class<?> clazz : scannedClass) {
            String beanName = getBeanName(clazz);
            if (!beans.containsKey(beanName)) {
                try {
                    Object realInstance = createRealInstance(clazz);
                    beans.put(beanName, realInstance);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // 클래스명에서 첫 글자 소문자로 변환한 것으로 빈 네임 통일
    private String getBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Ut.str.lcfirst(simpleName);
    }

    // 단일 생성자만 지원
    private Constructor<?> getTargetConstructor(Class<?> clazz) throws NoSuchMethodException {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        if (declaredConstructors.length == 1) {
            return declaredConstructors[0];
        }
        throw new RuntimeException("생성자가 2개 이상임: " + clazz.getName());
    }

    private Object createRealInstance(Class<?> clazz) throws Exception {
        Constructor<?> constructor = getTargetConstructor(clazz);
        Class<?>[] paramTypes = constructor.getParameterTypes();

        Object[] dependencies = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            String dependencyName = getBeanName(paramTypes[i]);
            Object dependency = genBean(dependencyName);

            if (dependency == null) {
                dependency = createRealInstance(paramTypes[i]);
                beans.put(dependencyName, dependency);
            }

            dependencies[i] = dependency;
        }

        return constructor.newInstance(dependencies);
    }

    //t7, t8 추가 코드(@Bean 메소드)
    private void registerBeanMethods(Set<Class<?>> scannedClass){
        for (Class<?> clazz : scannedClass) {
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(Bean.class)) {
                    Object instance = beans.get(getBeanName(clazz));
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    Object[] parameters = new Object[parameterTypes.length];

                    for (int i = 0; i < parameterTypes.length; i++) {
                        Class<?> parameterType = parameterTypes[i];
                        String paramBeanName = getBeanName(parameterType);
                        if (!beans.containsKey(paramBeanName)) {
                            registerComponentClass(Set.of(parameterType));
                        }
                        parameters[i] = beans.get(paramBeanName);
                    }

                    try {
                        Object invoke = method.invoke(instance, parameters);
                        beans.put(Ut.str.lcfirst(method.getName()), invoke);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public void init() {
        // 필요시 초기화 로직
    }

    public <T> T genBean(String beanName) {
        return (T) beans.getOrDefault(beanName, null);
    }
}
```
</details>
<br><br>

---

<br><br>

## 🆚 Spring과 기능 비교

### @Autowired 없음

원래라면 다음과 같이 `isAnnotationPresent`를 통해 @Autowired가 붙어있는지 확인하고,  
생성자, 메소드들의 매개변수들 중에서 @Autorwired가 붙은 부분을 골라 주입해야 할 것이다.

```java
// 단일 생성자만 지원 : 현재 시나리오에서는 @Autowired가 존재하지 않음
    private Constructor<?> getTargetConstructor(Class<?> clazz) {
        Constructor<?>[] declaredConstructors = clazz.getDeclaredConstructors();
        if (declaredConstructors.length == 1) {
            return declaredConstructors[0];
        } 
        //@Autowired가 있을시 추가 코드
        else {
            for (Constructor<?> constructor : declaredConstructors) {
                if (constructor.isAnnotationPresent(AutoWired.class)) {
                    return constructor;
                }
            }
        }
        throw new RuntimeException("생성자가 없음 : " + clazz.getName());
    }

```

### 순환참조 감지후 프록시 적용? -> Spring 2.6 이후부터 적용 X

Spring은 생성자 주입에 대해 순환참조를 감지하고, 이를 Proxy 패턴을 통해

순환적인 의존관계를 끊어내 대해서도 bean을 등록한다고 한다.

현재 코드에서는 단순히 순환참조의 감지만을 한다.

-> 스프링도 2.6버전부터 생성자 주입에 대한 순환참조를 기본적으로 지원하지 않고
설정을 통해 바꿀 수 있다고 한다.

다만, 기본적으로 지원하지 않고 순환 참조 설정이 가능한 필드 주입이나 세터 주입은 자체로도 권장되지 않으므로

순환참조가 일어나지 않도록 설계하는 것이 좋을 것 같다.


### 인터페이스도 등록 가능

스프링은 빈에 인터페이스의 등록도 가능하며 인터페이스를 구현한 구체클래스가 여러개 등록되어 있을 때도

다양한 선택전략에 따라 주입받게 된다.

1. @Qualifer로 등록된 빈 이름을 명시하여 주입받는다.

2. @Primary가 붙어있다면 우선순위가 적용된다.

3. 선언 변수명이 빈 등록 이름과 같으면 해당 구체클래스로 주입된다.

+ List나 Map으로 구체클래스를 모두 주입 받을 수도 있다.

### 그 외에 Spring bean의 부가적 기능

- @Component("myTestService")로 직접 빈이름 설정 가능
- 스코프 설정을 통해 싱글톤 외에도 프로토타입과 session, request 등 웹관련 스코프 설정 가능
- @PostConstruct, @PreDestroy를 통해 빈 초기화 직후, 소멸 직전에 콜백 메서드 호출 가능
- @Conditional을 기반으로 조건부 빈 생성을 지원하며, Spring Boot에서는 @ConditionalOnClass, @ConditionalOnProperty 같은 편의 애노테이션도 제공한다.
- AOP를 통해 런타임에 빈을 프록시로 감싸 로깅, 트랜잭션, 보안 같은 부가 기능을 투명하게 적용할 수 있다.
