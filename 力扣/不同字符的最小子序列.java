class Solution {
    public String smallestSubsequence(String s) {
        int[] count = new int[26];
        for (char c : s.toCharArray()) {
            count[c - 'a']++;
        }

        boolean[] visited = new boolean[26];
        
        StringBuilder stack = new StringBuilder();

        for (char c : s.toCharArray()) {
            count[c - 'a']--;

            if (visited[c - 'a']) {
                continue;
            }

            while (stack.length() > 0 
                && stack.charAt(stack.length() - 1) > c 
                && count[stack.charAt(stack.length() - 1) - 'a'] > 0) {
                
                char top = stack.charAt(stack.length() - 1);
                stack.deleteCharAt(stack.length() - 1); 
                visited[top - 'a'] = false;
            }


            stack.append(c);
            visited[c - 'a'] = true;
        }

        return stack.toString();
    }
}