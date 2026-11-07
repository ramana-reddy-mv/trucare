package com.trucare.member.controller;

import com.trucare.member.service.MemberService;
import com.trucare.shared.member.MemberDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    @Autowired
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @PostMapping("/addMember")
    public ResponseEntity<MemberDTO> createMember(@RequestBody MemberDTO memberDTO) {
        MemberDTO createdMember = memberService.createMember(memberDTO);
        return ResponseEntity.ok(createdMember);
    }

//    @GetMapping("/{id}")
//    public ResponseEntity<MemberDTO> getMemberById(@PathVariable String memberId) {
//        MemberDTO member = memberService.getMemberById(memberId);
//        return ResponseEntity.ok(member);
//    }

    @GetMapping("/{memberId}")
    public ResponseEntity<MemberDTO> getMemberByUsername(@PathVariable String memberId,
                                                         Authentication authentication) {
        MemberDTO member = memberService.getMemberByMemberId(memberId);
        boolean privileged = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_SERVICE"));
        if (!privileged && (member.getUsername() == null
                || !member.getUsername().equalsIgnoreCase(authentication.getName()))) {
            throw new AccessDeniedException("Members can only access their own profile");
        }
        return ResponseEntity.ok(member);
    }

    @GetMapping("/validate/{memberId}")
    public Boolean validateMember(@PathVariable String memberId) {
        return memberService.validateMember(memberId);
    }

    @GetMapping
    public ResponseEntity<List<MemberDTO>> getAllMembers() {
        List<MemberDTO> members = memberService.getAllMembers();
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MemberDTO> updateMember(@PathVariable Long id, @RequestBody MemberDTO memberDTO) {
        MemberDTO updatedMember = memberService.updateMember(id, memberDTO);
        return ResponseEntity.ok(updatedMember);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMember(@PathVariable Long id) {
        memberService.deleteMember(id);
        return ResponseEntity.noContent().build();
    }
}
