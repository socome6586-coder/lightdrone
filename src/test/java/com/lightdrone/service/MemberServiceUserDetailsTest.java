package com.lightdrone.service;

import com.lightdrone.domain.Member;
import com.lightdrone.domain.enums.Role;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceUserDetailsTest {

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private QnaRepository qnaRepository;
    @Mock
    private InquiryRepository inquiryRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private NoticeRepository noticeRepository;
    @Mock
    private ManualRepository manualRepository;

    @Test
    void loadUserByUsernameReflectsDisabledMemberStatus() {
        Member disabledMember = Member.builder()
                .username("blocked")
                .password("{noop}password")
                .name("Blocked User")
                .email("blocked@example.com")
                .role(Role.USER)
                .enabled(false)
                .build();

        when(memberRepository.findByUsername("blocked")).thenReturn(Optional.of(disabledMember));

        UserDetails userDetails = memberService().loadUserByUsername("blocked");

        assertThat(userDetails.isEnabled()).isFalse();
    }

    @Test
    void loadUserByUsernameKeepsEnabledAdminRole() {
        Member admin = Member.builder()
                .username("admin")
                .password("{noop}password")
                .name("Admin")
                .email("admin@example.com")
                .role(Role.ADMIN)
                .enabled(true)
                .build();

        when(memberRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        UserDetails userDetails = memberService().loadUserByUsername("admin");

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    private MemberService memberService() {
        return new MemberService(
                memberRepository,
                passwordEncoder,
                cartItemRepository,
                orderRepository,
                reviewRepository,
                qnaRepository,
                inquiryRepository,
                chatRoomRepository,
                chatMessageRepository,
                noticeRepository,
                manualRepository
        );
    }
}
