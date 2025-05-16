package back.util;


public record SfedLoginResult(String moodleSession, String errorCode, String detailedErrorMessage) {
    public boolean isSuccess() {
        return "SUCCESS".equals(errorCode) && moodleSession != null;
    }
} 