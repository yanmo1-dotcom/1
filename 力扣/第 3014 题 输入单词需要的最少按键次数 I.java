class Solution {
    public int minimumPushes(String word) {
        // 1. 统计每个字母的出现频率
        int[] freq = new int[26];
        for (char c : word.toCharArray()) {
            freq[c - 'a']++;
        }
        
        // 2. 将频率数组从大到小排序
        // 注意：Arrays.sort 默认是从小到大，我们需要手动反转或者使用 Integer 数组
        // 这里为了简单，先排序再倒序遍历，或者直接用 Integer 数组降序排
        Integer[] freqBoxed = new Integer[26];
        for (int i = 0; i < 26; i++) {
            freqBoxed[i] = freq[i];
        }
        Arrays.sort(freqBoxed, Collections.reverseOrder());
        
        // 3. 贪心计算总按键数
        int totalPushes = 0;
        int keyPosition = 1; // 当前处于第几个按键位置（1代表按1次，2代表按2次...）
        int countInCurrentPosition = 0; // 当前按键位置已经分配了多少个字母
        
        for (int f : freqBoxed) {
            if (f == 0) break; // 频率为0的字母不需要处理
            
            // 累加按键次数：频率 * 当前所需的按键次数
            totalPushes += f * keyPosition;
            
            countInCurrentPosition++;
            
            // 电话键盘有 8 个键 (2-9)
            // 如果当前这一层（比如都按1次的那些）已经分满了8个字母
            // 就要进入下一层（按2次的那些）
            if (countInCurrentPosition == 8) {
                keyPosition++;
                countInCurrentPosition = 0;
            }
        }
        
        return totalPushes;
    }
}