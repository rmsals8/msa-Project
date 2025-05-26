package com.example.auth_service.auth_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.sender}")
    private String sender;

    @Value("${app.email.sender-name:Schedule Maker}")
    private String senderName;

    /**
     * ✅ 회원가입 인증번호 이메일 발송 (비동기)
     * 호출 즉시 반환, 백그라운드에서 이메일 발송 처리
     */
    @Async("emailTaskExecutor")
    public void sendEmailVerificationCode(String to, String code) {
        try {
            log.info("🚀 회원가입 인증 이메일 발송 시작: {}", to);
            long startTime = System.currentTimeMillis();

            String subject = "[Schedule Maker] 회원가입 인증번호";
            String content = getEmailVerificationContent(code);

            sendEmailInternal(to, subject, content);

            long endTime = System.currentTimeMillis();
            log.info("✅ 회원가입 인증 이메일 발송 완료: {} (처리시간: {}ms)", to, (endTime - startTime));
        } catch (Exception e) {
            log.error("❌ 회원가입 인증 이메일 발송 실패: {}", to, e);
            // 비동기이므로 예외를 던지지 않음 (로그만 기록)
        }
    }

    /**
     * ✅ 비밀번호 재설정 코드를 이메일로 발송 (비동기)
     * 호출 즉시 반환, 백그라운드에서 이메일 발송 처리
     */
    @Async("emailTaskExecutor")
    public void sendPasswordResetCode(String to, String code) {
        try {
            log.info("🚀 비밀번호 재설정 이메일 발송 시작: {}", to);
            long startTime = System.currentTimeMillis();

            String subject = "[Schedule Maker] 비밀번호 재설정 인증번호";
            String content = getBasicPasswordResetEmailContent(code);

            sendEmailInternal(to, subject, content);

            long endTime = System.currentTimeMillis();
            log.info("✅ 비밀번호 재설정 이메일 발송 완료: {} (처리시간: {}ms)", to, (endTime - startTime));
        } catch (Exception e) {
            log.error("❌ 비밀번호 재설정 이메일 발송 실패: {}", to, e);
            // 비동기이므로 예외를 던지지 않음 (로그만 기록)
        }
    }

    /**
     * ✅ 비밀번호 변경 성공 알림 이메일 발송 (비동기)
     * 호출 즉시 반환, 백그라운드에서 이메일 발송 처리
     */
    @Async("emailTaskExecutor")
    public void sendPasswordChangedNotification(String to) {
        try {
            log.info("🚀 비밀번호 변경 알림 이메일 발송 시작: {}", to);
            long startTime = System.currentTimeMillis();

            String subject = "[Schedule Maker] 비밀번호가 변경되었습니다";
            String content = getPasswordChangedEmailContent();

            sendEmailInternal(to, subject, content);

            long endTime = System.currentTimeMillis();
            log.info("✅ 비밀번호 변경 알림 이메일 발송 완료: {} (처리시간: {}ms)", to, (endTime - startTime));
        } catch (Exception e) {
            log.error("❌ 비밀번호 변경 알림 이메일 발송 실패: {}", to, e);
            // 비동기이므로 예외를 던지지 않음 (로그만 기록)
        }
    }

    /**
     * ✅ 이메일 발송 내부 메소드 (실제 SMTP 통신)
     * 모든 이메일 발송 로직의 공통 처리
     * 
     * @param to          수신자 이메일
     * @param subject     이메일 제목
     * @param htmlContent HTML 형식의 이메일 내용
     * @throws MessagingException           SMTP 통신 오류
     * @throws UnsupportedEncodingException 인코딩 오류
     */
    private void sendEmailInternal(String to, String subject, String htmlContent)
            throws MessagingException, UnsupportedEncodingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(sender, senderName);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true); // HTML 형식 사용

        // 실제 SMTP 서버를 통한 이메일 발송
        mailSender.send(message);
    }

    /**
     * ✅ 회원가입 인증번호 이메일 HTML 템플릿
     * 깔끔하고 전문적인 디자인으로 사용자 경험 향상
     * 
     * @param code 6자리 인증번호
     * @return HTML 형식의 이메일 내용
     */
    private String getEmailVerificationContent(String code) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>회원가입 인증번호</title>"
                + "<style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f4f4f4; }"
                + ".container { max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden; }"
                + ".header { background: linear-gradient(135deg, #3498db, #2980b9); color: white; padding: 30px 20px; text-align: center; }"
                + ".header h2 { margin: 0; font-size: 24px; font-weight: 600; }"
                + ".content { padding: 40px 30px; background-color: #ffffff; }"
                + ".content p { margin: 0 0 20px 0; font-size: 16px; color: #555; }"
                + ".code-container { background-color: #f8f9fa; border: 2px dashed #3498db; border-radius: 10px; padding: 25px; margin: 30px 0; text-align: center; }"
                + ".code { font-size: 36px; font-weight: bold; color: #3498db; margin: 10px 0; letter-spacing: 8px; font-family: 'Courier New', monospace; }"
                + ".code-label { font-size: 14px; color: #7f8c8d; margin-bottom: 10px; }"
                + ".warning { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 4px; }"
                + ".warning p { margin: 0; color: #856404; font-size: 14px; }"
                + ".footer { background-color: #ecf0f1; padding: 20px; text-align: center; border-top: 1px solid #bdc3c7; }"
                + ".footer p { margin: 5px 0; font-size: 12px; color: #7f8c8d; }"
                + ".logo { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class=\"container\">"
                + "<div class=\"header\">"
                + "<h2>🎉 Schedule Maker 회원가입</h2>"
                + "</div>"
                + "<div class=\"content\">"
                + "<p>안녕하세요!</p>"
                + "<p>Schedule Maker에 가입해 주셔서 감사합니다. 회원가입을 완료하기 위해 아래 인증번호를 입력해주세요.</p>"
                + "<div class=\"code-container\">"
                + "<div class=\"code-label\">인증번호</div>"
                + "<div class=\"code\">" + code + "</div>"
                + "</div>"
                + "<div class=\"warning\">"
                + "<p>⏰ <strong>중요:</strong> 이 인증번호는 발송 시점으로부터 <strong>5분간만 유효</strong>합니다.</p>"
                + "</div>"
                + "<p>인증번호를 입력하신 후 회원가입을 완료해주세요. 문제가 있으시면 고객센터로 연락 부탁드립니다.</p>"
                + "</div>"
                + "<div class=\"footer\">"
                + "<div class=\"logo\">Schedule Maker</div>"
                + "<p>&copy; 2025 Schedule Maker. All rights reserved.</p>"
                + "<p>본 메일은 발신 전용입니다. 회신하지 마세요.</p>"
                + "<p>서울시 강남구 테헤란로 123 (가상 주소)</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    /**
     * ✅ 비밀번호 재설정 인증번호 이메일 HTML 템플릿
     * 보안 강조 디자인으로 사용자 신뢰도 향상
     * 
     * @param code 6자리 인증번호
     * @return HTML 형식의 이메일 내용
     */
    private String getBasicPasswordResetEmailContent(String code) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>비밀번호 재설정 인증번호</title>"
                + "<style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f4f4f4; }"
                + ".container { max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden; }"
                + ".header { background: linear-gradient(135deg, #e74c3c, #c0392b); color: white; padding: 30px 20px; text-align: center; }"
                + ".header h2 { margin: 0; font-size: 24px; font-weight: 600; }"
                + ".security-icon { font-size: 48px; margin-bottom: 10px; }"
                + ".content { padding: 40px 30px; background-color: #ffffff; }"
                + ".content p { margin: 0 0 20px 0; font-size: 16px; color: #555; }"
                + ".code-container { background-color: #fff5f5; border: 2px solid #e74c3c; border-radius: 10px; padding: 25px; margin: 30px 0; text-align: center; }"
                + ".code { font-size: 36px; font-weight: bold; color: #e74c3c; margin: 10px 0; letter-spacing: 8px; font-family: 'Courier New', monospace; }"
                + ".code-label { font-size: 14px; color: #7f8c8d; margin-bottom: 10px; }"
                + ".security-warning { background-color: #fdf2e9; border-left: 4px solid #e67e22; padding: 20px; margin: 25px 0; border-radius: 4px; }"
                + ".security-warning h4 { margin: 0 0 10px 0; color: #d35400; font-size: 16px; }"
                + ".security-warning p { margin: 5px 0; color: #a0522d; font-size: 14px; }"
                + ".timer-warning { background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin: 20px 0; border-radius: 4px; }"
                + ".timer-warning p { margin: 0; color: #856404; font-size: 14px; }"
                + ".footer { background-color: #ecf0f1; padding: 20px; text-align: center; border-top: 1px solid #bdc3c7; }"
                + ".footer p { margin: 5px 0; font-size: 12px; color: #7f8c8d; }"
                + ".logo { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class=\"container\">"
                + "<div class=\"header\">"
                + "<div class=\"security-icon\">🔒</div>"
                + "<h2>비밀번호 재설정</h2>"
                + "</div>"
                + "<div class=\"content\">"
                + "<p>안녕하세요!</p>"
                + "<p>Schedule Maker 계정의 비밀번호 재설정을 요청하셨습니다. 아래 인증번호를 입력하여 새로운 비밀번호를 설정해주세요.</p>"
                + "<div class=\"code-container\">"
                + "<div class=\"code-label\">인증번호</div>"
                + "<div class=\"code\">" + code + "</div>"
                + "</div>"
                + "<div class=\"timer-warning\">"
                + "<p>⏰ <strong>유효시간:</strong> 이 인증번호는 <strong>5분간만 유효</strong>합니다.</p>"
                + "</div>"
                + "<div class=\"security-warning\">"
                + "<h4>🛡️ 보안 안내</h4>"
                + "<p>• 본인이 요청하지 않은 경우, 이 이메일을 무시하고 삭제해주세요.</p>"
                + "<p>• 인증번호를 타인과 공유하지 마세요.</p>"
                + "<p>• 의심스러운 활동이 발견되면 즉시 고객센터로 연락하세요.</p>"
                + "</div>"
                + "</div>"
                + "<div class=\"footer\">"
                + "<div class=\"logo\">Schedule Maker</div>"
                + "<p>&copy; 2025 Schedule Maker. All rights reserved.</p>"
                + "<p>본 메일은 발신 전용입니다. 회신하지 마세요.</p>"
                + "<p>고객센터: support@schedulemaker.com | 1588-1234</p>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }

    /**
     * ✅ 비밀번호 변경 완료 알림 이메일 HTML 템플릿
     * 성공적인 변경을 축하하는 긍정적인 디자인
     * 
     * @return HTML 형식의 이메일 내용
     */
    private String getPasswordChangedEmailContent() {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>비밀번호 변경 완료</title>"
                + "<style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; line-height: 1.6; color: #333; margin: 0; padding: 0; background-color: #f4f4f4; }"
                + ".container { max-width: 600px; margin: 20px auto; background-color: #ffffff; border-radius: 10px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); overflow: hidden; }"
                + ".header { background: linear-gradient(135deg, #27ae60, #229954); color: white; padding: 30px 20px; text-align: center; }"
                + ".header h2 { margin: 0; font-size: 24px; font-weight: 600; }"
                + ".success-icon { font-size: 64px; margin-bottom: 15px; animation: bounce 2s infinite; }"
                + "@keyframes bounce { 0%, 20%, 50%, 80%, 100% { transform: translateY(0); } 40% { transform: translateY(-10px); } 60% { transform: translateY(-5px); } }"
                + ".content { padding: 40px 30px; background-color: #ffffff; text-align: center; }"
                + ".content p { margin: 0 0 20px 0; font-size: 16px; color: #555; }"
                + ".success-message { background-color: #d5f4e6; border: 2px solid #27ae60; border-radius: 10px; padding: 30px 20px; margin: 30px 0; }"
                + ".success-message h3 { margin: 0 0 15px 0; color: #1e8449; font-size: 20px; }"
                + ".success-message p { margin: 0; color: #186a3b; font-size: 16px; }"
                + ".timestamp { background-color: #f8f9fa; border-radius: 5px; padding: 15px; margin: 25px 0; }"
                + ".timestamp p { margin: 0; font-size: 14px; color: #6c757d; }"
                + ".security-notice { background-color: #fef9e7; border-left: 4px solid #f39c12; padding: 20px; margin: 25px 0; border-radius: 4px; }"
                + ".security-notice h4 { margin: 0 0 10px 0; color: #d68910; font-size: 16px; }"
                + ".security-notice p { margin: 5px 0; color: #b7950b; font-size: 14px; }"
                + ".footer { background-color: #ecf0f1; padding: 20px; text-align: center; border-top: 1px solid #bdc3c7; }"
                + ".footer p { margin: 5px 0; font-size: 12px; color: #7f8c8d; }"
                + ".logo { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 10px; }"
                + ".contact-info { margin-top: 15px; padding-top: 15px; border-top: 1px solid #bdc3c7; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class=\"container\">"
                + "<div class=\"header\">"
                + "<div class=\"success-icon\">✅</div>"
                + "<h2>비밀번호 변경 완료</h2>"
                + "</div>"
                + "<div class=\"content\">"
                + "<div class=\"success-message\">"
                + "<h3>🎉 변경 성공!</h3>"
                + "<p>회원님의 비밀번호가 성공적으로 변경되었습니다.</p>"
                + "</div>"
                + "<p>이제 새로운 비밀번호로 안전하게 로그인하실 수 있습니다.</p>"
                + "<div class=\"timestamp\">"
                + "<p><strong>변경 시간:</strong> "
                + java.time.LocalDateTime.now().toString().replace("T", " ").substring(0, 19) + "</p>"
                + "</div>"
                + "<div class=\"security-notice\">"
                + "<h4>🛡️ 보안 확인</h4>"
                + "<p>• 본인이 변경하지 않은 경우, 즉시 고객센터로 연락해주세요.</p>"
                + "<p>• 정기적인 비밀번호 변경으로 계정을 안전하게 보호하세요.</p>"
                + "<p>• 다른 사이트와 동일한 비밀번호 사용을 피해주세요.</p>"
                + "</div>"
                + "<p>Schedule Maker를 이용해 주셔서 감사합니다!</p>"
                + "</div>"
                + "<div class=\"footer\">"
                + "<div class=\"logo\">Schedule Maker</div>"
                + "<p>&copy; 2025 Schedule Maker. All rights reserved.</p>"
                + "<p>본 메일은 발신 전용입니다. 회신하지 마세요.</p>"
                + "<div class=\"contact-info\">"
                + "<p><strong>고객센터</strong></p>"
                + "<p>이메일: support@schedulemaker.com</p>"
                + "<p>전화: 1588-1234 (평일 09:00-18:00)</p>"
                + "</div>"
                + "</div>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}