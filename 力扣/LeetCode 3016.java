import java.util.Arrays;

class Solution {
    public int minimumPushes(String word) {
        // 1. 统计每个字母的出现频率
        int[] counts = new int[26];
        for (char c : word.toCharArray()) {
            counts[c - 'a']++;
        }
        
        // 2. 将频率数组从大到小排序
        // 注意：Arrays.sort 默认是升序，我们需要手动处理或反转
        // 这里使用 Integer 数组方便降序排序，或者排序后倒着遍历
        Integer[] freq = new Integer[26];
        for (int i = 0; i < 26; i++) {
            freq[i] = counts[i];
        }
        Arrays.sort(freq, (a, b) -> b - a); // 降序排序
        
        // 3. 贪心计算最少按键数
        int totalPushes = 0;
        for (int i = 0; i < 26; i++) {
            if (freq[i] == 0) break; // 后面都是0了，没必要继续
            
            // 有8个按键(2-9)，所以每8个字母为一轮
            // 第0-7个字母按1次，第8-15个按2次...
            int presses = (i / 8) + 1;
            
            totalPushes += freq[i] * presses;
        }
        
        return totalPushes;
    }
}