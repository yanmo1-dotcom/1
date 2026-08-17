class Solution {
    // 【关键修改】方法名必须严格匹配报错信息中的 smallestPalindrome
    public String smallestPalindrome(String s, int k) {
        // 1. 统计字符频率
        int[] counts = new int[26];
        for (char c : s.toCharArray()) {
            counts[c - 'a']++;
        } 

        // 2. 准备左半部分的字符池
        int[] halfCounts = new int[26];
        char midChar = 0; // 中间字符
        int halfLen = 0;  // 左半部分长度

        for (int i = 0; i < 26; i++) {
            if (counts[i] % 2 != 0) {
                midChar = (char) ('a' + i);
            }
            halfCounts[i] = counts[i] / 2;
            halfLen += halfCounts[i];
        }

        // 边界情况：如果左半部分长度为0（如 "a", "aa"），只有一种回文
        if (halfLen == 0) {
            return k == 1 ? s : "";
        }

        // 3. 贪心构造左半部分
        char[] leftPart = new char[halfLen];
        long currentK = k; // 使用 long 防止减法溢出

        for (int i = 0; i < halfLen; i++) {
            boolean found = false;
            // 尝试从 'a' 到 'z' 填入当前位置
            for (int j = 0; j < 26; j++) {
                if (halfCounts[j] == 0) continue;

                // 假设当前位置填入字符 j
                halfCounts[j]--; 
                
                // 计算剩余位置能组成的排列数
                long permutations = getPermutations(halfCounts, halfLen - 1 - i);

                if (currentK <= permutations) {
                    // 第 k 个排列就在当前分支下
                    leftPart[i] = (char) ('a' + j);
                    found = true;
                    break; 
                } else {
                    // 当前分支不够，跳过
                    currentK -= permutations;
                    halfCounts[j]++; // 回溯，恢复计数
                }
            }
            
            // 如果遍历完所有字符都没找到，说明 k 超出了总排列数
            if (!found) return "";
        }

        // 4. 拼接最终结果
        StringBuilder sb = new StringBuilder();
        sb.append(leftPart);
        
        // 添加中间字符
        if (midChar != 0) {
            sb.append(midChar);
        }
        
        // 添加右半部分（左半部分的逆序）
        sb.append(new StringBuilder(new String(leftPart)).reverse());

        return sb.toString();
    }

    /**
     * 计算多重集排列数: n! / (c1! * c2! * ... * c26!)
     * 如果结果超过 limit (10^9 + 1)，直接返回 limit，防止溢出
     */
    private long getPermutations(int[] counts, int totalRemaining) {
        long res = 1;
        int remaining = totalRemaining;
        final long LIMIT = 1000000001L; 

        for (int count : counts) {
            if (count == 0) continue;
            
            // 计算组合数 C(remaining, count)
            // C(n, k) = n * (n-1) * ... * (n-k+1) / k!
            long comb = 1;
            int k = Math.min(count, remaining - count);
            
            for (int x = 0; x < k; x++) {
                // 使用 double 进行中间计算以处理大数，只要判断是否超过 LIMIT
                double temp = (double)comb * (remaining - x) / (x + 1);
                if (temp > LIMIT) return LIMIT;
                comb = (long)temp;
            }
            
            res *= comb;
            if (res > LIMIT) return LIMIT;
            
            remaining -= count;
        }
        
        return res;
    }
}