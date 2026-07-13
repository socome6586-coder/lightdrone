package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.Role;
import com.lightdrone.dto.AdminMemberEditDto;
import com.lightdrone.dto.ProfileUpdateDto;
import com.lightdrone.dto.SignupDto;
import com.lightdrone.domain.ChatRoom;
import com.lightdrone.repository.CartItemRepository;
import com.lightdrone.repository.ChatMessageRepository;
import com.lightdrone.repository.ChatRoomRepository;
import com.lightdrone.repository.InquiryRepository;
import com.lightdrone.repository.ManualRepository;
import com.lightdrone.repository.MemberRepository;
import com.lightdrone.repository.NoticeRepository;
import com.lightdrone.repository.OrderRepository;
import com.lightdrone.repository.QnaRepository;
import com.lightdrone.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService implements UserDetailsService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final ReviewRepository reviewRepository;
    private final QnaRepository qnaRepository;
    private final InquiryRepository inquiryRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final NoticeRepository noticeRepository;
    private final ManualRepository manualRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        return new User(
                member.getUsername(),
                member.getPassword(),
                member.isEnabled(),
                true,
                true,
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole().name()))
        );
    }

    @Transactional
    public Member signup(SignupDto dto) {
        if (memberRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        if (memberRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        if (!dto.getPassword().equals(dto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        if (dto.getPassword().equals(dto.getUsername())) {
            throw new IllegalArgumentException("비밀번호는 아이디와 동일하게 설정할 수 없습니다.");
        }

        Member member = Member.builder()
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .zipCode(dto.getZipCode())
                .address(dto.getAddress())
                .addressDetail(dto.getAddressDetail())
                .role(Role.USER)
                .agreePrivacy(dto.isAgreePrivacy())
                .agreeThirdParty(dto.isAgreeThirdParty())
                .agreeOutsourcing(dto.isAgreeOutsourcing())
                .build();

        return memberRepository.save(member);
    }

    public boolean isUsernameAvailable(String username) {
        return !memberRepository.existsByUsername(username);
    }

    public boolean isEmailAvailable(String email) {
        return !memberRepository.existsByEmail(email);
    }

    public boolean isPhoneAvailable(String phone) {
        return !memberRepository.existsByPhone(phone);
    }

    /* ── 아이디 찾기 ── */
    public Optional<String> findUsernameByNameAndEmail(String name, String email) {
        return memberRepository.findByNameAndEmail(name, email)
                .map(Member::getUsername);
    }

    public Optional<String> findUsernameByNameAndPhone(String name, String phone) {
        return memberRepository.findByNameAndPhone(name, phone)
                .map(Member::getUsername);
    }

    /* ── 비밀번호 찾기: 본인 확인 ── */
    public boolean verifyForPasswordReset(String username, String name, String email) {
        return memberRepository.findByUsernameAndNameAndEmail(username, name, email).isPresent();
    }

    public boolean verifyForPasswordResetByPhone(String username, String name, String phone) {
        return memberRepository.findByUsernameAndNameAndPhone(username, name, phone).isPresent();
    }

    /* ── 비밀번호 재설정 ── */
    @Transactional
    public void resetPassword(String username, String newPassword) {
        Member member = findByUsername(username);
        member.setPassword(passwordEncoder.encode(newPassword));
    }

    /* ── 아이디 마스킹 (예: testuser → te****er) ── */
    public String maskUsername(String username) {
        if (username == null || username.length() <= 2) return username;
        int visible = Math.max(2, username.length() / 3);
        String stars = "*".repeat(username.length() - visible);
        return username.substring(0, visible) + stars;
    }

    public List<Member> findAll() {
        return memberRepository.findAllByOrderByCreatedAtDesc();
    }

    /** 통합검색용 — 이름·아이디·이메일·연락처 부분일치 (최신 가입순, limit 건) */
    public List<Member> searchForAdmin(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return List.of();
        return memberRepository.searchForAdmin(keyword.trim(),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public long count() {
        return memberRepository.count();
    }

    public long countToday() {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return memberRepository.countByCreatedAtBetween(start, end);
    }

    public Member findById(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
    }

    /** 관리자: 회원 정보 수정 */
    @Transactional
    public void adminUpdateMember(Long id, AdminMemberEditDto dto) {
        Member member = findById(id);
        member.setName(dto.getName());
        member.setEmail(dto.getEmail());
        member.setPhone(dto.getPhone());
        member.setRole(dto.getRole());
        if (dto.getBusinessGrade() != null) {
            member.setBusinessGrade(dto.getBusinessGrade());
        }
        member.setEnabled(dto.isEnabled());
        if (StringUtils.hasText(dto.getNewPassword())) {
            member.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }
    }

    /** 관리자: 회원 삭제 (연관 데이터 선처리 후 삭제) */
    @Transactional
    public void deleteMember(Long id) {
        Member member = memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다."));
        // 1) 장바구니 항목 삭제
        cartItemRepository.deleteAllByMember(member);
        // 2) 주문 내역: member 참조만 해제 (주문 데이터 보존)
        orderRepository.detachMember(member);
        // 3) 리뷰 삭제
        reviewRepository.deleteAllByMember(member);
        // 4) Q&A 삭제
        qnaRepository.deleteAllByMember(member);
        // 5) 1:1 문의 삭제
        inquiryRepository.deleteAllByMember(member);
        // 6) 1:1 채팅: 메시지 → 방 순으로 정리 (FK NOT NULL 제약 해제)
        //    - 회원이 개설한 방의 모든 메시지(관리자 답변 포함) 선삭제
        for (ChatRoom room : chatRoomRepository.findByMember(member)) {
            chatMessageRepository.deleteAllByRoom(room);
        }
        //    - 회원이 다른 방에서 보낸 메시지(관리자였던 경우 등)도 삭제
        chatMessageRepository.deleteAllBySender(member);
        //    - 회원이 개설한 방 삭제
        chatRoomRepository.deleteAllByMember(member);
        // 7) 참조만 해제하는 일괄 업데이트(영속성 컨텍스트 clear 동반)는 마지막에 수행.
        //    이후에는 member 엔티티 대신 id 만 사용한다.
        //    - 회원이 담당 관리자로 배정된 방의 admin 참조 해제
        chatRoomRepository.detachAdmin(member);
        //    - 공지/자료실 작성자 참조 해제 (콘텐츠 보존)
        noticeRepository.detachAuthor(member);
        manualRepository.detachAuthor(member);
        // 8) 회원 삭제 (id 기반)
        memberRepository.deleteById(id);
    }

    public Member findByUsername(String username) {
        return memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    /** 소셜 로그인 회원의 실제 이메일 입력(추가정보) 처리 */
    @Transactional
    public void updateEmail(String username, String email) {
        Member member = findByUsername(username);
        // 다른 회원이 이미 사용 중인 이메일인지 확인 (본인 현재 이메일은 허용)
        memberRepository.findByEmail(email).ifPresent(other -> {
            if (!other.getId().equals(member.getId())) {
                throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
            }
        });
        member.setEmail(email);
    }

    /** 회원 탈퇴 (개인정보 익명화 처리, 주문 내역은 유지) */
    @Transactional
    public void withdraw(String username, String rawPassword) {
        Member member = findByUsername(username);
        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 올바르지 않습니다.");
        }
        // 개인정보 익명화 (주문 내역 보존을 위해 hard delete 대신 anonymize)
        member.setEnabled(false);
        member.setName("탈퇴회원");
        member.setEmail("withdrawn_" + member.getId() + "@deleted.com");
        member.setPhone(null);
        member.setZipCode(null);
        member.setAddress(null);
        member.setAddressDetail(null);
        member.setPassword("{noop}WITHDRAWN_" + member.getId());
    }

    /**
     * 비밀번호 검증 (verify 페이지에서 사용)
     */
    public boolean verifyPassword(String username, String rawPassword) {
        Member member = findByUsername(username);
        return passwordEncoder.matches(rawPassword, member.getPassword());
    }

    /**
     * 휴대전화번호 + 배송지 + 비밀번호 수정 (비밀번호 인증은 verify 단계에서 이미 완료)
     */
    @Transactional
    public void updateProfile(String username, ProfileUpdateDto dto) {
        Member member = findByUsername(username);

        // 연락처
        if (dto.getPhone() != null) {
            member.setPhone(dto.getPhone());
        }

        // 배송지
        member.setZipCode(dto.getZipCode());
        member.setAddress(dto.getAddress());
        member.setAddressDetail(dto.getAddressDetail());

        // 비밀번호 변경 (입력된 경우에만)
        if (StringUtils.hasText(dto.getNewPassword())) {
            if (!dto.getNewPassword().equals(dto.getNewPasswordConfirm())) {
                throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
            }
            member.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }
    }
}
