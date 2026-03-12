package com.hwan.coupon.member.dto;

import com.hwan.coupon.member.Role;

public record LoginResponse(Long id, String email, String name, Role role) {}