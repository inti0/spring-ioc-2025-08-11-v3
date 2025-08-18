package com.ll.standard.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class Ut {
    public static class str {
        public static String lcfirst(String str) {
            if (str == null || str.isEmpty()) {
                return str;
            }

            char firstChar = str.charAt(0);

            if (Character.isLowerCase(firstChar)) {
                return str;
            }

            return Character.toLowerCase(firstChar) + str.substring(1);
        }
    }

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
}
