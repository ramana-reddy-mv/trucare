package com.trucare.member.service;

import com.trucare.member.mapper.MemberMapper;
import com.trucare.member.model.Member;
import com.trucare.member.repository.MemberRepository;
import com.trucare.shared.member.MemberDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberMapper memberMapper;

    @InjectMocks
    private MemberServiceImpl memberService;

    private Member member;
    private MemberDTO memberDTO;

    @BeforeEach
    void setup() {
        member = new Member(1L, "ramanar", "ramana", "reddy",
                LocalDate.of(1996, 6, 25), "MALE", "7799150283", "Bangalore", List.of(1L, 2L));

        memberDTO = new MemberDTO(1L, "M123456", "ramanar", "ramana", "reddy",
                LocalDate.of(1996, 6, 25), "MALE", "7799150283", "Bangalore", List.of(1L, 2L));
    }

    @Test
    void createMemberSuccessTest(){
        when(memberRepository.existsByUsername(memberDTO.getUsername())).thenReturn(false);
        when(memberMapper.toDO(memberDTO)).thenReturn(member);
        when(memberRepository.save(member)).thenReturn(member);
        when(memberMapper.toDTO(member)).thenReturn(memberDTO);

        MemberDTO res = memberService.createMember(memberDTO);

        assertNotNull(res);
        assertEquals(1L, res.getId());
        assertEquals("ramanar",res.getUsername());

        // Verify order of method calls
        InOrder inOrder = inOrder(memberRepository, memberMapper);
        inOrder.verify(memberRepository).existsByUsername(memberDTO.getUsername());
        inOrder.verify(memberMapper).toDO(memberDTO);
        inOrder.verify(memberRepository).save(member);
        inOrder.verify(memberMapper).toDTO(member);
    }

    @Test
    void memberMapperIncludesGeneratedBusinessIdInResponses() {
        Member persisted = new Member();
        persisted.setId(42L);
        persisted.setMemberId("M123456");
        persisted.setUsername("ramanar");

        MemberDTO response = MemberMapper.INSTANCE.toDTO(persisted);

        assertEquals("M123456", response.getMemberId());
    }

    @Test
    void createMemberFailTest(){
        when(memberRepository.existsByUsername(memberDTO.getUsername())).thenReturn(true);

        Exception exception = assertThrows(RuntimeException.class, () -> memberService.createMember(memberDTO));

        assertEquals("Username already exists.",exception.getMessage());

        verify(memberRepository, times(1)).existsByUsername(memberDTO.getUsername());
        verify(memberMapper, never()).toDO(any());
        verify(memberRepository, never()).save(any());
        verify(memberMapper, never()).toDTO(any());
    }




    @Test
    void getMemberByIdSuccessTest(){
        //given
        Member member = new Member(1L, "ramanar","ramana","reddy", LocalDate.of(1996, 6,25),"MALE","7799150283","Banglore", List.of(1L,2L));
        MemberDTO memberDTO =  new MemberDTO(1L, "M123456", "ramanar","ramana","reddy", LocalDate.of(1996, 6,25),"MALE","7799150283","Banglore", List.of(1L,2L));

        when(memberRepository.findById(1L)).thenReturn(Optional.of(member)); //✅ Mock DB
        when(memberMapper.toDTO(member)).thenReturn(memberDTO); // MOck mapping

        //when
        MemberDTO res = memberService.getMemberById(1L);

        //then
        assertNotNull(res);
        assertEquals(1L, res.getId());
        assertEquals("ramanar",res.getUsername());
        verify(memberRepository, times(1)).findById(1L);
        verify(memberMapper, times(1)).toDTO(member);
    }

    @Test
    void getMemberByIdFailTest(){
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class, ()-> memberService.getMemberById(99L));

        assertEquals("Member not found with id 99",exception.getMessage());
    }

    @Test
    void deleteMemberSuccessTest(){
        when(memberRepository.findById(member.getId())).thenReturn(Optional.ofNullable(member));
        doNothing().when(memberRepository).delete(member);
        //execute the service method
        assertDoesNotThrow(() -> memberService.deleteMember(member.getId()));

        //verify interactions
        verify(memberRepository, times(1)).findById(member.getId());
        verify(memberRepository, times(1)).delete(member);
    }
    @Test
    void deleteMemberFailTest(){
        when(memberRepository.findById(3L)).thenReturn(Optional.empty());

        Exception exception = assertThrows(RuntimeException.class,
                ()->memberService.deleteMember(3L));

        assertEquals("Member not found with id 3", exception.getMessage());

        // Verify that findById() was called exactly once
        verify(memberRepository, times(1)).findById(3L);

        // Verify that delete() was NEVER called
        verify(memberRepository, never()).delete(any(Member.class));
    }
}
