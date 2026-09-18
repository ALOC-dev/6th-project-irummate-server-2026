package com.irummate.domain.user.service;

import com.irummate.domain.user.dto.UserDetailsRequestDto;
import com.irummate.domain.user.dto.UserDetailsResponseDto;
import com.irummate.domain.user.dto.UserDetailsUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileResponseDto;
import com.irummate.domain.user.dto.UserProfileUpdateRequestDto;
import com.irummate.domain.user.dto.UserProfileUpdateResponseDto;
import com.irummate.domain.user.entity.UserDetails;
import com.irummate.domain.user.entity.UserRole;
import com.irummate.domain.user.entity.Users;
import com.irummate.domain.user.repository.UserDetailsRepository;
import com.irummate.domain.user.repository.UsersRepository;
import com.irummate.global.exception.BusinessException;
import com.irummate.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @Service: ?ㅽ봽留곸뿉 ???대옒?ㅺ? 鍮꾩쫰?덉뒪 濡쒖쭅???대떦?섎뒗 ?쒕퉬??而댄룷?뚰듃?꾩쓣 ?깅줉?⑸땲??
 * @RequiredArgsConstructor: final ?꾨뱶????媛쒖쓽 ?섏〈??DB ??μ냼)???먮룞?쇰줈 二쇱엯諛쏆뒿?덈떎.
 * @Transactional(readOnly = true): ?곗씠?곕쿋?댁뒪瑜??쎄린 ?꾩슜?쇰줈 ?ㅻ０ ??湲곕낯媛믪쑝濡??곸슜?⑸땲??
 * ???뺣텇??議고쉶 ?깅뒫怨??덉젙?깆씠 議곌툑 ??醫뗭븘吏묐땲?? (媛믪쓣 諛붽씀??硫붿꽌?쒖뿉??蹂꾨룄濡?@Transactional??遺숈쓬)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserDetailsService {


    private final UsersRepository usersRepository;
    private final UserDetailsRepository userDetailsRepository;

    /**
     * ??븷: ?ъ슜?먯쓽 異붽? ?뺣낫(?깅챸, ?숇쾲, ?섏씠 ??瑜?理쒖큹濡???ν빀?덈떎.
     * @Transactional: 媛믪쓽 ?섏젙/?앹꽦???쇱뼱?섎?濡? 以묎컙???먮윭媛 ?섎㈃ ?꾨? 痍⑥냼?섎룄濡??꾩쟾???몃옖??뀡???곷땲??
     */
    @Transactional
    public UserDetailsResponseDto createDetails(Long userId, UserDetailsRequestDto request) {
        Long userPk = Long.valueOf(userId);
        // ?대? ?곸꽭 ?뺣낫媛 ?깅줉?섏뿀?붿? 寃?ы빀?덈떎.
        if (userDetailsRepository.existsById(userPk)) {
            // ?대? 議댁옱?쒕떎硫??덉쇅(409 Conflict)瑜?諛쒖깮?쒗궢?덈떎.
            throw new BusinessException(ErrorCode.DETAILS_ALREADY_EXISTS);
        }

        // ?좏겙?먯꽌 爰쇰궦 userId媛 ?ㅼ젣濡?議댁옱?섎뒗 ?좎??몄? ?뺤씤?⑸땲??
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); // ?놁쑝硫?404 ?먮윭

        // ?낅젰諛쏆? ?곗씠??Dto)瑜?諛뷀깢?쇰줈 ?ㅼ젣 DB ?뚯씠釉?紐⑥뼇??媛앹껜(Entity)瑜??앹꽦?⑸땲??
        UserDetails userDetails = UserDetails.builder()
                .user(user)
                .realName(request.getRealName())
                .studentId(request.getStudentId())
                .age(request.getAge())
                .gender(request.getGender())
                .department(request.getDepartment())
                .phoneNumber(request.getPhoneNumber())
                .build();

        // [DB ??? ?앹꽦??媛앹껜瑜??곗씠?곕쿋?댁뒪?????Insert)?⑸땲??
        UserDetails savedDetails = userDetailsRepository.save(userDetails);
        if(user.getRole() == UserRole.GUEST){
            user.promoteToUser();
        }

        return toResponse(savedDetails);
    }

    @Transactional
    public UserDetailsResponseDto updateDetails(Long userId, UserDetailsUpdateRequestDto request) {
        Long userPk = Long.valueOf(userId);

        UserDetails userDetails = userDetailsRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_DETAILS_NOT_FOUND));

        userDetails.update(
                request.getRealName(),
                request.getStudentId(),
                request.getAge(),
                request.getGender(),
                request.getDepartment(),
                request.getPhoneNumber()
        );

        return toResponse(userDetails);
    }
    /**
     * ?좎? 蹂몄씤???꾨줈???뺣낫瑜?議고쉶?⑸땲??
     */
    public UserProfileResponseDto getProfile(Long userId) {
        Long userPk = Long.valueOf(userId);
        // 1. 湲곕낯 ?좎? ?뺣낫(?대찓?? ?됰꽕????瑜?DB?먯꽌 李얠뒿?덈떎.
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 異붽? ?곸꽭 ?뺣낫(?숇쾲, ?숆낵 ????DB?먯꽌 李얠뒿?덈떎.
        // 異붽? ?뺣낫媛 ?꾩쭅 ?놁쓣 ?섎룄 ?덉쑝誘濡? ?놁쑝硫?null濡?泥섎━?⑸땲??orElse(null) 泥섎━瑜??⑸땲??
        UserDetails userDetails = userDetailsRepository.findById(userPk).orElse(null);

        // 3. ???뚯씠釉붿뿉 ?닿릿 ?뺣낫瑜?UserProfileResponseDto)濡?臾띠뼱???뚮젮以띾땲??
        return UserProfileResponseDto.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .role(user.getRole().name())
                .status(user.getStatus().name())
                .detail(toProfileDetail(userDetails))
                .build();
    }

    /**
     * ?좎????꾨줈???됰꽕?? ?꾨줈???대?吏)瑜??섏젙?⑸땲??
     * @Transactional: DB ?곗씠?곗쓽 ?ㅼ젣 蹂寃?Update)???쇱뼱?섎?濡??몃옖??뀡???꾩슂?⑸땲??
     */
    @Transactional
    public UserProfileUpdateResponseDto updateProfile(Long userId, UserProfileUpdateRequestDto request) {
        Long userPk = Long.valueOf(userId);
        // 1. ?섏젙???좎?瑜?DB?먯꽌 李얠뒿?덈떎.
        Users user = usersRepository.findById(userPk)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. ?좏깮???꾨뱶 泥섎━: 媛믪씠 ?덉쑝硫?媛앹껜??媛믪쓣 諛붽퓠?덈떎.
        // ?ㅽ봽留??몃옖??뀡 ?덉뿉??媛앹껜??媛믪씠 諛붾뚮㈃, 硫붿꽌?쒓? ?앸궇 ???먮룞?쇰줈 DB??UPDATE 荑쇰━媛 ?섍컩?덈떎.
        user.updateProfile(request.getNickname(), request.getProfileImageUrl());

        // 3. ?섏젙 ?꾨즺??寃곌낵瑜??댁븘 諛섑솚?⑸땲??
        return UserProfileUpdateResponseDto.builder()
                .nickname(user.getNickname())
                .profileImageUrl(user.getProfileImageUrl())
                .build();
    }



    /**
     * DB???뷀떚??UserDetails)瑜??붾㈃ 諛섑솚??UserDetailsResponseDto)?쇰줈 蹂?섑빐以띾땲??
     */
    private UserDetailsResponseDto toResponse(UserDetails userDetails) {
        return UserDetailsResponseDto.builder()
                .realName(userDetails.getRealName())
                .studentId(userDetails.getStudentId())
                .age(userDetails.getAge())
                .gender(userDetails.getGender())
                .department(userDetails.getDepartment())
                .phoneNumber(userDetails.getPhoneNumber())
                .build();
    }

    /**
     * ?좎? ?꾨줈??議고쉶 ?? ?곸꽭 ?뺣낫媛 ?ㅼ뼱?덈뒗 寃쎌슦?먮쭔 ?곸꽭 ?뺣낫 Dto瑜??앹꽦?⑸땲?? (?뺣낫媛 ?놁쑝硫??꾩쟾?섍쾶 null 諛섑솚)
     */
    private UserProfileResponseDto.Detail toProfileDetail(UserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }

        return UserProfileResponseDto.Detail.builder()
                .realName(userDetails.getRealName())
                .studentId(userDetails.getStudentId())
                .age(userDetails.getAge())
                .gender(userDetails.getGender())
                .department(userDetails.getDepartment())
                .phoneNumber(userDetails.getPhoneNumber())
                .build();
    }
}

