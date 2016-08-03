$(function()
{
    var schemePrefArray = ['Empty'],
    firstEmptyPosition = $.inArray('Empty', schemePrefArray),
    preferencesAs123 = ['1st', '2nd', '3rd', '4th', '5th'],
    preferencesAsText = [
      '1st preference',
      '2nd preference',
      '3rd preference',
      '4th preference',
      '5th preference'
    ];
    $('[data-schemename]').on('change', function()
      {
        var $this = $(this),
        thisScheme = $this.closest('.block-label').text(),
        thisSchemeID = $this.attr('id'),
        thisSchemeValue = $this.attr('value'),
        schemeReq = $this.closest('.scheme-container').find(
            '[data-scheme-req]').html(),
        isSpecial = $this.closest('.scheme-container').find(
            '[data-spec-scheme]').length,
        specialEligibility = isSpecial == 0 ?
            '<p class="font-xsmall no-btm-margin">Requires at least a ' +
        schemeReq + '</p>' :
            '<div class="scheme-warning text"><p class="font-xsmall">Requires at least a ' +
        schemeReq + '</p></div>',
        arrayPosition = $.inArray(thisSchemeID, schemePrefArray),
        emptyPosition = $.inArray('Empty', schemePrefArray);
        if (arrayPosition >= 0)
        {
          //Do nothing
        }
        else if ($this.is(':checked'))
        {
          if (emptyPosition < 0)
          {
            schemePrefArray.push(thisSchemeID);
          }
          else
          {
            schemePrefArray.splice(emptyPosition, 1, thisSchemeID);
          }
          var arrayPositionNow = $.inArray(thisSchemeID,
            schemePrefArray);
          var priority = arrayPositionNow + 1;
          var schemePriorityId = "#scheme_"+priority;
          $(schemePriorityId).val(thisSchemeValue);
          $('#selectedPrefList li').eq(arrayPositionNow).after(
              '<li class="med-btm-margin scheme-prefcontainer" data-scheme-id="' +
              thisSchemeID + '"><span data-schemeorder>' +
              preferencesAsText[arrayPositionNow] +
              '</span><div class="width-all-1-1"><span class="bold-small" data-schemenameinlist>' +
              thisScheme + '</span>' + specialEligibility +
              '</div>'
          );
          $this.closest('.scheme-container').addClass(
            'selected-scheme').find('.selected-preference').text(
            preferencesAs123[arrayPositionNow]).removeClass(
            'invisible');
        }
        if (!$this.is(':checked'))
        {
          var priority = arrayPosition + 1;
          var schemePriorityId = "#scheme_"+priority;
          $(schemePriorityId).val('');
          schemePrefArray.splice(arrayPosition, 1, 'Empty');
          $('#selectedPrefList').find('[data-scheme-id="' +
            thisSchemeID + '"]').remove();
          $this.closest('.scheme-container').removeClass(
            'selected-scheme').find('.selected-preference').text(
            'N/A').addClass('invisible');
        }
        var chosenPreferences = $('[data-schemeorder]').map(
            function()
            {
              return $(this).text();
            })
          .get();
        var arrayOfChosen = $.makeArray(chosenPreferences);
        var differenceArray = [],
          initialVal = 0;
        $.grep(preferencesAsText, function(el)
        {
          if ($.inArray(el, arrayOfChosen) == -1)
            differenceArray.push(el);
          initialVal++;
        })
        if (differenceArray[0] === undefined)
        {
          $('#chosenLimit').removeClass('hidden');
          $('#chooseYourPrefs').addClass('hidden');
        }
        else
        {
          $('#chosenLimit').addClass('hidden');
          $('#chooseYourPrefs').removeClass('hidden');
          $('#chooseNextPreference').text(differenceArray[0]);
          if (differenceArray[0] !== "1st preference")
          {
            $('[data-optionalappended]').removeClass('hidden');
          }
          else
          {
            $('[data-optionalappended]').addClass('hidden');
          }
        }
        if ($('input[data-schemename]:checked').length == 5)
        {
          $('input[data-schemename]:not(:checked)').attr(
            'disabled', true).closest('.block-label').addClass(
            'disabled');
        }
        else
        {
          $('input[data-schemename]:not(:checked)').attr(
            'disabled', false).closest('.block-label').removeClass(
            'disabled');
        }
        if ($('input[data-schemename]:checked').length > 0)
        {
          $('[data-scheme-placeholder]').addClass(
            'toggle-content');
        }
        else
        {
          $('[data-scheme-placeholder]').removeClass(
            'toggle-content');
        }
      });
      function sticky_relocate()
      {
        if ($('.global-header__title').css('font-size') !== '16px')
        {
          var window_top = $(window).scrollTop(),
            div_top = $('#sticky-anchor').offset().top,
            li_top = $('.schemes-list li:last-child .block-label').offset()
            .top,
            li_bottom = li_top + $(
              '.schemes-list li:last-child .block-label').outerHeight(),
            el_height = $('.scheme-info-panel').outerHeight(),
            bottomThreshold = window_top + el_height + 5;
          if (window_top > div_top)
          {
            $('.scheme-info-panel').addClass('fixed-panel');
            $('#sticky-anchor').height(el_height);
            if (li_bottom <= bottomThreshold)
            {
              $('.scheme-info-panel').removeClass('fixed-panel').addClass(
                'bottom-panel').css('bottom', '12px');
            }
            else
            {
              $('.scheme-info-panel').removeClass('bottom-panel').addClass(
                'fixed-panel').removeAttr('style');
            }
          }
          else
          {
            $('.scheme-info-panel').removeClass('fixed-panel');
            $('#sticky-anchor').height(0);
          }
        }
      }
      $(window).scroll(sticky_relocate);
      sticky_relocate();

      function preselectSchemes(){
        for(i = 1; i <=5; i++){
            var scheme = $('#scheme_'+i).val();
            if(scheme !== ''){
                console.log(scheme);
                var sid = "#schemes_" + scheme;
                var initialStatus = $(sid).is(':checked');
                if(initialStatus == false){
                    $(sid).click();
                }
                $(sid).checked = true;
                $(sid).trigger('change');
            }
        }
      }
      $(document).ready(
            preselectSchemes()
      );
});