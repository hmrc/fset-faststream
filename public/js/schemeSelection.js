$(function () {
  var schemePrefArray = ['Empty'];
  var maxSchemes = 3;
  var numberOfSchemes = $('[data-schemename]').length;

  function getGetOrdinal(n) {
    var s=["th","st","nd","rd"],
    v=n%100;
    return n+(s[(v-20)%10]||s[v]||s[0]);
  }

  $('[data-schemename]').on('change', function () {
    var $this = $(this);
    var thisScheme = $this.closest('.block-label').text();
    var thisSchemeID = $this.attr('id');
    var thisSchemeValue = $this.attr('value');
    var schemeReq = $this.closest('.scheme-container').find('[data-scheme-req]').html();
    var isCivilServant = $this.closest('.scheme-container').find('[data-civil-servant-scheme]').length;
    var civilServant = isCivilServant > 0;
    var isSpecial = $this.closest('.scheme-container').find('[data-spec-scheme]').length;

    var specialEligibility = isSpecial === 0 ? '<p class="font-xsmall no-btm-margin">Requires at least a ' + schemeReq + '</p>' :
      '<div class="scheme-warning text"><p class="font-xsmall">Requires at least a ' + schemeReq + '</p></div>';

    var arrayPosition = $.inArray(thisSchemeID, schemePrefArray);
    var emptyPosition = $.inArray('Empty', schemePrefArray);

    if (arrayPosition < 0 && $this.is(':checked')) {
      if (emptyPosition < 0) {
        schemePrefArray.push(thisSchemeID);
      } else {
        schemePrefArray.splice(emptyPosition, 1, thisSchemeID);
      }

      var arrayPositionNow = $.inArray(thisSchemeID, schemePrefArray);
      var hiddenSchemeId = "#scheme_" + arrayPositionNow;
      $(hiddenSchemeId).val(thisSchemeValue);

      if (civilServant === true) {
        $('#selectedPrefList li').eq(arrayPositionNow).after(
          '<li class="med-btm-margin scheme-prefcontainer" data-scheme-id="' +
          thisSchemeID + '"><span data-schemeorder>' +
          getGetOrdinal(arrayPositionNow + 1) +
          '</span><div class="text scheme-elegrepeat"><span class="bold-small" data-schemenameinlist>' +
          thisScheme +
          '</span><p>You\'re eligible as a current civil servant</p>' +
          '<a href="#" class="link-unimp scheme-remove"><i class="fa fa-times" aria-hidden="true"></i>Remove <span class="visuallyhidden">' + thisScheme + '</span></a>' +
          '</div>'
        );
      } else {
        $('#selectedPrefList li').eq(arrayPositionNow).after(
          '<li class="med-btm-margin scheme-prefcontainer" data-scheme-id="' +
          thisSchemeID + '"><span data-schemeorder>' +
          getGetOrdinal(arrayPositionNow + 1) +
          ' preference</span><div class="text scheme-elegrepeat"><span class="bold-small" data-schemenameinlist>' +
          thisScheme + '</span>' + specialEligibility +
          '<a href="#" class="link-unimp scheme-remove"><i class="fa fa-times" aria-hidden="true"></i>Remove <span class="visuallyhidden">' + thisScheme + '</span></a>' +
          '</div>'
        );
      }

      $this.closest('.scheme-container').addClass('selected-scheme')
        .find('.selected-preference').text(getGetOrdinal(arrayPositionNow + 1)).removeClass('invisible');

      if($('[data-schemename]:checked').length === maxSchemes) {
        $('[data-schemename]:not(:checked)').attr('disabled', true).closest('label').addClass('disabled');
        $('#selectedPrefList').after('<div id="schemeWarning" class="panel-info standard-panel"><p>You\'ve chosen the maximum number of schemes</p></div>');
      }
    }

    if (!$this.is(':checked')) {
      var hiddenSchemeId = "#scheme_" + arrayPosition;
      $(hiddenSchemeId).val('');
      schemePrefArray.splice(arrayPosition, 1, 'Empty');
      $('#selectedPrefList').find('[data-scheme-id="' +
        thisSchemeID + '"]').remove();
      $this.closest('.scheme-container').removeClass(
        'selected-scheme').find('.selected-preference').text(
        'N/A').addClass('invisible');

      $('[data-schemename]:not(:checked)').attr('disabled', false).closest('label').removeClass('disabled');
      $('#schemeWarning').remove();
    }

    $('#chosenLimit').addClass('hidden');
    $('#chooseYourPrefs').removeClass('hidden');

    if ($('input[data-schemename]:checked').length > 0) {
      $('[data-scheme-placeholder]').addClass(
        'toggle-content');
    } else {
      $('[data-scheme-placeholder]').removeClass(
        'toggle-content');
    }
  });


  $('#selectedPrefList').on('click', '.scheme-remove', function (e) {
    var thisScheme = $(this).closest('.scheme-prefcontainer').attr('data-scheme-id'),
      schemesAfter = $(this).closest('.scheme-prefcontainer').nextAll(),
      schemeName = $(this).closest('.scheme-prefcontainer').find('[data-schemenameinlist]').text();

    e.preventDefault();

    $('#' + thisScheme).trigger('click').attr('checked', false).closest('label').removeClass('selected');

    $('#schemeRemovedMessage').text(schemeName + ' has been removed from your preferences');

    schemesAfter.each(function () {
      var schemeID = $(this).attr('data-scheme-id');
      var scheme = $('#' + schemeID);
      scheme.trigger('click');
      scheme.trigger('click');
    });
  });

  function selectSchemes() {
    for (var i = 0; i < numberOfSchemes; i++) {
      var scheme = $('#scheme_' + i).val();
      if (scheme !== '') {
        var sid = "#schemes_" + scheme;
        var initialStatus = $(sid).is(':checked');
        if (initialStatus === false) {
          $(sid).click();
        }
        $(sid).checked = true;
        $(sid).trigger('change');
      }
    }
  }

  selectSchemes();
});