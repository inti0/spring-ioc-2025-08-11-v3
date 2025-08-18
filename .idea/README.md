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

### 위상 정렬

```java
public static class topologicalSort {
    /** 위상 정렬로 빈 이름을 정렬한다.
     * @param edges = ["testRepository testService", ...]
     *              (B가 A에 의존하는, A가 있어야 B를 생성할 수 있는 형태로 제시된다.)
     * @return 의존성이 작은 bean 이름부터 반환된다.
     */
    public static Queue<String> sort(List<String> edges) {
        Map<String, List<String>> adjacencyLists = drawAdjacencyLists(edges);
        Map<String, Integer> indegrees = calculateIndegree(adjacencyLists);

        Queue<String> result = new LinkedList<>();
        Queue<String> queue = new LinkedList<>();

        indegrees.entrySet().stream().filter(entry -> entry.getValue() == 0)
                .forEach(entry -> queue.add(entry.getKey()));

        while (!queue.isEmpty()) {
            String vertex = queue.poll();
            result.add(vertex);

            List<String> adjacencyList = adjacencyLists.getOrDefault(vertex, new LinkedList<>());
            for (String adjacencyVertex : adjacencyList) {
                indegrees.merge(adjacencyVertex, -1, Integer::sum);

                if (indegrees.getOrDefault(adjacencyVertex, 0) == 0) {
                    queue.add(adjacencyVertex);
                }
            }
        }
        return result;
    }

    private static Map<String, List<String>> drawAdjacencyLists(List<String> edges) {
        Map<String, List<String>> adjacencyLists = new HashMap<>();

        for (String edge : edges) {
            String[] split = edge.split(" ");
            String startNode = split[0]; //의존이 필요한 객체, 나중에 생성 되어야 한다
            String endNode = split[1];   //의존성을 제공하는 객체, 먼저 생성 되어야 한다
            adjacencyLists.putIfAbsent(startNode, new ArrayList<>());

            adjacencyLists.get(startNode).add(endNode);
        }
        return adjacencyLists;
    }

    private static Map<String, Integer> calculateIndegree(Map<String, List<String>> adjacencyLists) {
        Map<String, Integer> indegrees = new HashMap<>();

        for (String node : adjacencyLists.keySet()) {
            indegrees.put(node, 0);
        }

        for (List<String> neighbors : adjacencyLists.values()) {
            for (String neighbor : neighbors) {
                indegrees.merge(neighbor, 1, Integer::sum);
            }
        }
        return indegrees;
    }
}
```

순환참조를 해결하기 위해 Proxy를 학습하고 Proxy로 구현하고자 하였다.


<details>
<summary>Proxy를 이용한 구현 시도 -> 원하는 대로 구현 성공하고 동작 확인 했으나, t5, t6 통과 못함
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

--- 

<details>
<summary> t1 ~ t8 통과하는 코드 작성 (tdd green) </summary>

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

---