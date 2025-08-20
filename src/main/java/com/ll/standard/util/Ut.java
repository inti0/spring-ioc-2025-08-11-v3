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
        /**
         * 주어진 문자열 목록을 위상 정렬한다.
         * @param edges 의존 관계를 나타내는 문자열 리스트.
         *              각 문자열은 "testRepository testService", "A B" 형식이며, 띄어쓰기를 구분자로 한다.
         *              "A B"는 "B가 A에 의존한다"는 뜻으로,
         *              B를 생성하기 전에 A가 먼저 존재해야 함을 의미한다.
         * @return 의존성이 적은 노드부터 순서대로 정렬된 결과
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
                String startNode = split[0];
                String endNode = split[1];
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
