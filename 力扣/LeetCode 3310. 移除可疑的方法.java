import java.util.*;

class Solution {
    // 注意：这里返回值改成了 List<Integer>
    public List<Integer> remainingMethods(int n, int k, int[][] invocations) {
        
        // 1. 构建邻接表
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] inv : invocations) {
            int from = inv[0], to = inv[1];
            graph.get(from).add(to);
        }

        // 2. BFS 找出所有可疑方法
        boolean[] isSuspicious = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(k);
        isSuspicious[k] = true;
        
        while (!queue.isEmpty()) {
            int curr = queue.poll();
            for (int next : graph.get(curr)) {
                if (!isSuspicious[next]) {
                    isSuspicious[next] = true;
                    queue.offer(next);
                }
            }
        }

        // 3. 检查是否有“外部”调用（安全方法调用了可疑方法）
        for (int[] inv : invocations) {
            int from = inv[0], to = inv[1];
            // 如果目标(to)是可疑的，但来源(from)不是 -> 说明有外部依赖，无法移除
            if (isSuspicious[to] && !isSuspicious[from]) {
                // 无法移除，返回包含所有方法的 List
                List<Integer> allMethods = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    allMethods.add(i);
                }
                return allMethods;
            }
        }

        // 4. 收集剩下的安全方法
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!isSuspicious[i]) {
                result.add(i);
            }
        }
        
        return result;
    }
}