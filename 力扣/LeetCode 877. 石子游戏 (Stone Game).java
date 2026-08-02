class Solution {
    public boolean stoneGame(int[] piles) {
        int n = piles.length;
        // dp[i][j] 表示在 piles[i..j] 区间内，当前玩家相对于对手的分数差
        int[][] dp = new int[n][n];

        // 初始化：区间长度为1时，当前玩家只能取该堆
        for (int i = 0; i < n; i++) {
            dp[i][i] = piles[i];
        }

        // 按区间长度从小到大递推
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                int j = i + len - 1;
                // 选左端 piles[i]：获得 piles[i]，减去对手在 [i+1, j] 的最优差值
                // 选右端 piles[j]：获得 piles[j]，减去对手在 [i, j-1] 的最优差值
                dp[i][j] = Math.max(piles[i] - dp[i + 1][j], piles[j] - dp[i][j - 1]);
            }
        }

        // 如果 dp[0][n-1] > 0，说明先手（Alice）的分数严格大于后手（Bob）
        // 注意：题目说总数是奇数，所以不可能等于0，但写 >= 0 也没错
        return dp[0][n - 1] > 0;
    }
}