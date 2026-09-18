package com.irummate.domain.user.controller;

import com.irummate.domain.user.dto.UserDetailsRequestDto;
import com.irummate.domain.user.dto.UserDetailsResponseDto;
import com.irummate.domain.user.dto.UserDetailsUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileResponseDto;
import com.irummate.domain.user.dto.UserProfileUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileUpdateResponseDto;
import com.irummate.domain.user.service.UserDetailsService;
import com.irummate.global.response.GlobalApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserDetailsController {

    private final UserDetailsService userDetailsService;

    /**

     * @AuthenticationPrincipal:???り킔?洹먮뿫堉??熬곥굤??JwtAuthenticationFilter)?띠럾? ??ルㅎ荑???롪틵?嶺뚯빘鍮쒒뇡???     * ???깆쓧??怨몄땟 ?????PK ??userId)?????깆쓧???우벟 ?怨쀫닑亦?? 嶺뚮씞?녻뚯궘????琉욱뱺 ?낅슣????紐껊퉵??
     */
    @GetMapping("/me")
    public ResponseEntity<GlobalApiResponse<UserProfileResponseDto>> getProfile(
            @AuthenticationPrincipal Long userId
    ) {
        // 1. ??類λ룴???고뱺 ??? ID?????삳낵 ?熬곣뫁夷????⑥щ턄??? ?브퀗????紐껊퉵??
        UserProfileResponseDto response = userDetailsService.getProfile(userId);

        // 2. ?繹먭퍓沅???얜Ŧ堉?ApiResponse.success)???????HTTP ??⑤객臾?袁⑤?獄?200(OK)???꾩룇瑗???紐껊퉵??
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "\uC720\uC800 \uC815\uBCF4 \uC870\uD68C \uC131\uACF5", response));
    }

    /**
     * [PATCH] /api/users/me
     * ???? ?熬곣뫗???β돦裕??筌뤿굝由???????熬곣뫁夷????怨뚰맟?? ?熬곣뫁夷??????嶺뚯솘?)????瑜곸젧??紐껊퉵??
     * @RequestBody: ??????怨룹꽘?筌? ?곌랜?亦?JSON ??⑥щ턄??? ???類??띠룇鍮섊뙼?Dto)???곌떠???臾먰돵繞벿살뵯???
     */
    @PatchMapping("/me")
    public ResponseEntity<GlobalApiResponse<UserProfileUpdateResponseDto>> updateProfile(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserProfileUpdateRequestDto request
    ) {
        // 1. ??類λ룴???고뱺 ??? ID?? ??瑜곸젧????怨몃뮔(request)?????삳낵 ??⑥щ턄??? ???낆몥??袁⑤콦??紐껊퉵??
        UserProfileUpdateResponseDto response = userDetailsService.updateProfile(userId, request);

        // 2. ??瑜곸젧 ?熬곣뫁??嶺뚮∥???낆???? ??節띾쐾 ?띠럾???ㅻ쾳筌??롪퍒?????⑥щ턄??? ?꾩룇瑗???紐껊퉵??
        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "\uD504\uB85C\uD544 \uC218\uC815 \uC131\uACF5", response));
    }

    /**
     * [POST] /api/users/details
     * ???? ?곸궠?삭맱???β돦裕???嶺뚯쉳??? ??類λ룴????怨몃뮔???熬곣뫗????怨뺣뼺? ?リ옇????筌먲퐢沅??繹먮굞援? ???쀬벐, ??瑜곷턄, ?繹먮굟?? ???욧땁)??嶺뚣끉裕???繹먮굞夷??紐껊퉵??
     */
    @PostMapping("/details")
    public ResponseEntity<GlobalApiResponse<UserDetailsResponseDto>> createDetails(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDetailsRequestDto request
    ) {
        // 1. ??類λ룴???고뱺 ??? ID?? ?怨뺣뼺? ?筌먲퐢沅????놁졑 ??⑥щ턄??? ???삳낵 ???됱Ŧ????⑤㈇??筌먲퐢沅?UserDetails)????諛댁뎽??紐껊퉵??
        UserDetailsResponseDto response = userDetailsService.createDetails(userId, request);

        // 2. ????⑥щ턄??? ??諛댁뎽??琉??????HTTP ??⑤객臾?袁⑤?獄?201(CREATED)????얜Ŧ堉??紐껊퉵??
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GlobalApiResponse.success(HttpStatus.CREATED, "\uAE30\uBCF8 \uC815\uBCF4 \uB4F1\uB85D \uC131\uACF5", response));
    }
    /**
     * [PATCH] /api/users/details
     * ??? ?熬곣뫁夷????⑤㈇???筌먲퐢沅???瑜곸젧??紐껊퉵??
     */
    @PatchMapping("/details")
    public ResponseEntity<GlobalApiResponse<UserDetailsResponseDto>> updateDetails(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UserDetailsUpdateRequestDto request
    ) {
        UserDetailsResponseDto response = userDetailsService.updateDetails(userId, request);

        return ResponseEntity.ok(GlobalApiResponse.success(HttpStatus.OK, "\uAE30\uBCF8 \uC815\uBCF4 \uC218\uC815 \uC131\uACF5", response));
    }
}
