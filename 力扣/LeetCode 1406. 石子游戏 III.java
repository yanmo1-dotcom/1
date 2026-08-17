class Solution {
    public String stoneGameIII(int[] stoneValue) {
        int n = stoneValue.length;
        // dp[i] 表示从第 i 堆开始取，当前玩家相对于对手的最大分数差
        // 只需要往后看 3 个状态，所以可以用滚动变量优化空间，这里为了清晰使用数组
        // 注意：dp 数组大小设为 n + 3 以避免边界检查
        int[] dp = new int[n + 3]; 
        
        // 从后往前递推
        for (int i = n - 1; i >= 0; i--) {
            dp[i] = Integer.MIN_VALUE;
            int sum = 0;
            // 尝试取 1, 2, 3 堆
            for (int k = 1; k <= 3; k++) {
                if (i + k > n) break;
                sum += stoneValue[i + k - 1]; // 累加当前取的 k 堆的分数
                // 当前得分 sum 减去 对手在 i+k 处的最优差值 dp[i+k]
                dp[i] = Math.max(dp[i], sum - dp[i + k]);
            }
        }

        // dp[0] > 0: Alice 分数高
        // dp[0] < 0: Bob 分数高
        // dp[0] == 0: 平局
        if (dp[0] > 0) return "Alice";
        if (dp[0] < 0) return "Bob";
        return "Tie";
    }
}