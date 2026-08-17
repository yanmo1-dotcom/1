import java.unil.*;
import java.util.List;
import java.util.stream.Collectors;

public class ClassApiService {
    public static String getStudentsApi(String token) {
        String allowedGender = ClassDataStore.checkToken(token);
        if ("INVALID".equals(allowedGender)) {
            return "Error:403 Forbidden - Invalid Token";
        }
        List<Student> resultList;

        if (allowedGender == null) {
            // 管理员/班干部权限，返回所有学生
            resultList = ClassDataStore.STUDENT_DB;
        } else {
            // 根据性别过滤学生
            resultList = ClassDataStore.STUDENT_DB.stream()
                .filter(student -> student.getGender().equals(allowedGender))
                .collect(Collectors.toList());
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("\"code\":200,\n");
        json.append("\"count\":").append(resultList.size()).append(",\n");
        json.append("\"data\":[\n");

        for (int i = 0; i < resultList.size(); i++) {
            Student s = resultList.get(i);
            json.append(" {\"id\":").append(s.getId())
                .append(",\"name\":\"").append(s.getName()).append("\"")
                .append(",\"gender\":\"").append(s.getGender()).append("\"")
                .append("}");

            if (i < resultList.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("]\n");
        json.append("}");
        return json.toString();
    }
}