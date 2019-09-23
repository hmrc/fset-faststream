$(function () {
  // Variable used to track if there are any outstanding actions that webdriver should wait for during
  // the acceptance test runs
  window.csrActive = 0;

  'use strict';

  var ENTER_KEY = 13;
  var BAD_REQUEST = 400;
  var NOT_FOUND = 404;
  var addressLookupUrlBase = '/fset-fast-stream/address-search/';

  var _this = this; // scope
  _this.$addressLine1 = $('#address\\.line1');
  _this.$addressLine2 = $('#address_line2');
  _this.$addressLine3 = $('#address_line3');
  _this.$addressLine4 = $('#address_line4');
  _this.$addressSelect = $('#addressSelect');
  _this.$addressManualInput = $('#addressManualInput');
  _this.$findAddressBtn = $('#findAddressBtn');
  _this.$addressManualLink = $('#addressManualLink');
  _this.$postCode = $('#postCode');
  _this.$country = $('#country');
  _this.$postCodeErrorWrapper = $('#postCodeErrorWrapper');
  _this.$postCodeError = $('#postCodeError');
  _this.addressesFound = $('#addressesFound');
  _this.$postCodeEntry = $('#postCodeEntry');
  _this.$addressesSelectorContainer = $('#addressSelectorContainer');
  _this.$postCodeSearch = $('#post-code-search');

  function sanitisePostcode(postcode) {
    return postcode.toUpperCase().replace(/ /g, '');
  }

  function addressSearchByPostcode(postcode) {
    var url = addressLookupUrlBase + '?postcode=' + sanitisePostcode(postcode);

    $.getJSON(url, function (result) {
      _this.$addressSelect.append(
          '<option value=\'void\'>' + result.length + ' results found</option>'
      );

      $.map(result, function (lookupReply) {
        var address = lookupReply.address.lines[0] + ' ' + lookupReply.address.town;
        _this.$addressSelect.append(
            '<option value=\'' + lookupReply.id + '\'>' + address + '</option>'
        );
      });
      var addressSelector = _this.$addressesSelectorContainer;
      addressSelector.removeClass('toggle-content');
      addressSelector.slideDown('slow');

    }).fail(postCodeSearchFailHander);
  }

  function showResultsLink(num) {
    var link = _this.$addressesFound;
    if (num === 1) {
      link.text(num + ' result found');
    } else {
      link.text(num + ' results found');
    }

    link.removeClass('hidden');
  }

  function hideResultsLink() {
    var link = _this.addressesFound;
    link.text('');
    link.addClass('hidden');
  }

  function postCodeSearchFailHander(xhr, textStauts, error) {
    hideResultsLink();
    if (xhr.status === BAD_REQUEST) {
      showPostCodeError('Post code is not valid');
    } else if (xhr.status === NOT_FOUND) {
      showPostCodeError('No addresses found');
    }
  }

  function getAddressById(id, successFunction) {
    $.getJSON(addressLookupUrlBase + id,  successFunction).fail(postCodeSearchFailHander);
  }

  function showPostCodeError(text) {
    _this.$postCodeError.text(text);
    _this.$postCodeEntry.addClass('has-an-error input-validation-error');
    _this.$postCodeErrorWrapper.slideDown(300);
  }

  function hidePostCodeError() {
    _this.$postCodeEntry.removeClass('has-an-error input-validation-error');
    _this.$postCodeErrorWrapper.slideUp(300);
  }

  function populateAddressFields(addressRecord) {
    _this.$addressManualInput.removeClass('disabled');

    var addressLines = addressRecord.address.lines.slice(0, 3);
    addressLines.push(addressRecord.address.town);

    // the escaping here is because of the way we render the Ids from the Play field constructor.
    // In order for the 'jump to error' links on the page to work there is a dot in the Id, which is
    // interpreted as a class selector here.
    _this.$addressLine1.val(addressLines[0]);
    _this.$addressLine2.val(addressLines[1] || '');
    _this.$addressLine3.val(addressLines[2] || '');
    _this.$addressLine4.val(addressLines[3] || '');
    _this.$postCode.val(addressRecord.address.postcode);

    $('html, body').animate({
      scrollTop: _this.$addressManualInput.offset().top
    }, 400);

  }

  if (_this.$postCodeSearch != null) {
    $('#outsideUk').on('change', function() {
      _this.$addressLine1.val('');
      _this.$addressLine2.val('');
      _this.$addressLine3.val('');
      _this.$addressLine4.val('');
      _this.$postCode.val('');
      _this.$country.val('');

      if($(this).is(':checked')) {
        _this.$addressManualInput.removeClass('disabled');
        _this.$postCode.closest('.form-group').addClass('toggle-content');
        _this.$country.closest('.form-group').removeClass('toggle-content');
        _this.$addressLine1.focus();
      } else {
        _this.$postCode.closest('.form-group').removeClass('toggle-content');
        _this.$country.closest('.form-group').addClass('toggle-content');
      }
    });

    _this.$addressManualLink.on('click', function(e) {
      e.preventDefault();

      _this.$addressManualInput.removeClass('disabled');
      _this.$addressLine1.focus();
    });

    _this.$findAddressBtn.on('click', function (event) {
      event.preventDefault();
      _this.$addressSelect.empty();

      hidePostCodeError();
      _this.$postCodeError.text('');

      if (_this.$postCodeSearch.val().length) {
        addressSearchByPostcode(_this.$postCodeSearch.val());
      } else {
        showPostCodeError('Post code is not valid');
      }

      return false;
    });

    _this.$addressSelect.on('change', function (event) {
      event.preventDefault();

      if ($(this).val() != 'void') {
        $('#outsideUk:checked').trigger('click');
        var selectedAddressID = $($(this).find('option')[this.selectedIndex]).val();
        getAddressById(selectedAddressID, populateAddressFields);
      }
    });

    _this.$postCodeSearch.on('keydown', function (event) {
      if (event.keyCode === 13) {
        event.preventDefault();
        _this.$findAddressBtn.trigger('click');
        return false;
      }
    });
  }
});;/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/


$(function() {

  var isMobile = {
    Android: function() {
      return navigator.userAgent.match(/Android/i);
    },
    BlackBerry: function() {
      return navigator.userAgent.match(/BlackBerry/i);
    },
    iOS: function() {
      return navigator.userAgent.match(/iPhone|iPad|iPod/i);
    },
    Opera: function() {
      return navigator.userAgent.match(/Opera Mini/i);
    },
    Windows: function() {
      return navigator.userAgent.match(/IEMobile/i);
    },
    any: function() {
      return (isMobile.Android() || isMobile.BlackBerry() || isMobile.iOS() || isMobile.Opera() || isMobile.Windows());
    }
  };

  if (isMobile.any()) {
    FastClick.attach(document.body);

    $('input[autofocus]').removeAttr('autofocus');
  }

  function isAndroid() {
    var nua = navigator.userAgent,
        isAndroid = (nua.indexOf('Mozilla/5.0') > -1 && nua.indexOf('Android ') > -1 && nua.indexOf('AppleWebKit') > -1 && nua.indexOf('Chrome') === -1);
    if (isAndroid) {
      $('html').addClass('android-browser');
    }
  }

  isAndroid();

  // Fixes intermittent bug where a form submitted with errors sometimes takes the user back
  // to last place on page rather than to error summary
  if($('#validation-summary').is(':visible')) {
    window.csrActive++;
    setTimeout(function()
    {
      window.scrollTo(0, 0);
      window.csrActive--;
    }, 200);
  }

  $('.nav-menu__trigger').on('click', function() {
    $(this).next('.nav-menu__items').toggleClass('toggle-content');
    $(this).toggleClass('triggered');
    return false;
  });

  $('.mob-collpanel-trigger').on('click', function() {
    $(this).next('.mob-collpanel').toggleClass('panel-open');
    $(this).toggleClass('triggered');
    return false;
  });

  $('.collpanel-trigger').on('click', function() {
    $(this).next('.collpanel').toggleClass('panel-open');
    $(this).toggleClass('triggered');
    return false;
  });

  $('.button-toggler').on('click', function() {
    var $this = $(this),
        $target = $this.attr('data-target');

    $('#' + $target).toggleClass('toggle-content');

    return false;
  });

  // Create linked input fields (For using email address as username)
  $('.linked-input-master').on('keyup blur', function() {
    var masterVal = $(this).val();
    $('.linked-input-slave').val(masterVal);
    $('.linked-input-slave').removeClass('hidden').text(masterVal);
    if($(this).val() == '') {
      $('.linked-input-slave').addClass('hidden');
    }
  });

  $(".block-label").each(function(){
    var $target = $(this).attr('data-target');

    // Add focus
    $(".block-label input").focus(function() {
      $("label[for='" + this.id + "']").addClass("add-focus");
    }).blur(function() {
      $("label").removeClass("add-focus");
    });
    // Add selected class
    $('input:checked').parent().addClass('selected');

    if($(this).hasClass('selected')) {
      $('#' + $target).show();
    }
  });

  // Add/remove selected class
  $('.block-label').on('click', 'input[type=radio], input[type=checkbox]', function() {
    var $this   = $(this),
        $target = $this.parent().attr('data-target'),
        $siblingArray = [],
        $siblingTarget = '',
        $disTarget = $.trim($this.parent().attr('data-distarget')).split(" "),
        $theTargetControl = '';

    $this.closest('.form-group').find('.block-label').not($this.parent()).each(function() {
      $siblingArray.push('#' + $(this).attr('data-target'));
      $siblingTarget = $siblingArray.join(", ");
    });

    if($disTarget.length > 1) {
      var multipleTargets = $($disTarget.join(", "));
      $theTargetControl = multipleTargets;
    } else {
      $theTargetControl = $('#' + $disTarget);
    }

    $('input:not(:checked)').parent().removeClass('selected');
    $('input:checked').parent().addClass('selected');

    if($this.is('input[type=checkbox]')) {
      if($this.is(':checked')) {
        $('#' + $target).show();
      } else {
        $('#' + $target).hide();
      }
    } else {
      if($target == undefined) {
        $this.closest('.form-group').siblings('.toggle-content').hide().attr('aria-hidden', true);
        $this.closest('.form-group').find('[aria-expanded]').attr('aria-expanded', false);
      } else {
        $('#' + $target).show();
        $($siblingTarget).hide().attr('aria-hidden', true);

        if($this.closest('.form-group').hasClass('blocklabel-single')) {

          $this.closest('.blocklabel-single-container').find('.blocklabel-content').not('#' + $target).hide();
        }
      }
    }

    if($disTarget && $this.is(':checked')) {
      $theTargetControl.attr('disabled', true);
      if($theTargetControl.attr('type') == 'text') {
        $theTargetControl.val('');
      } else if($theTargetControl.is('select')) {
        $theTargetControl.find('> option:first-of-type').attr('selected', true);
      }
    } else if($disTarget && !$this.is(':checked')) {
      $theTargetControl.attr('disabled', false);
    }

  });

  function toggleTargetDisplayOnOptionSelection(selectBox){
    var optionTrigger = selectBox.find('.optionTrigger'),
        optionTarget = $('#' + optionTrigger.attr('data-optiontrigger'));
    if(optionTrigger.is(':selected')) {
      optionTarget.show();
    } else {
      optionTarget.hide();
    }
  }

  $('.selectWithOptionTrigger').on('change', function() {
       toggleTargetDisplayOnOptionSelection($(this))
  });

  $('.selectWithOptionTrigger').ready(function() {
       toggleTargetDisplayOnOptionSelection($(this))
  });

  $('.amend-answers').on('click', function() {
    $(this).closest('.form-group').toggleClass('expanded');
    return false;
  });

  $('.update-answers').on('click', function() {
    $(this).closest('.form-group').toggleClass('expanded');
  });

  $('.summary-trigger').on('click', function() {
    $('.summary-box').toggle();
  });

  $('.summary-close').on('click', function() {
    $('.summary-box').toggle();
  });

  $('.inpage-focus').on('click', function() {
    var $this      = $(this),
        $target    = $this.attr('href'),
        $targetFor = $($target).attr('for');

    $('#' + $targetFor).focus();
  });


  //--------Max character length on textareas

  $('textarea').on('keyup', function() {
    characterCount(this);
  });

  $('textarea:not(:empty)').each(function() {
    characterCount(this);
  });

  setTimeout(function() {
    $('textarea[data-value]').each(function() {
      characterCount(this);
    });
  }, 1000);

  function characterCount(that) {
    var $this         = $(that),
        $maxLength    = $this.attr('data-val-length-max'),
        $enteredText  = $this.val(),
        $lineBreaks   = ($enteredText.match(/\n/g) || []).length,
        $lengthOfText = $enteredText.length + $lineBreaks,
        $characterCount = Math.abs($maxLength - $lengthOfText),
        $charCountEl  = $this.closest('.form-group').find('.maxchar-count'),
        $charTextEl   = $this.closest('.form-group').find('.maxchar-text'),
        $thisAria     = $this.closest('.form-group').find('.aria-limit');

    if($maxLength) {
      $charCountEl.text($characterCount);
    }

    if($lengthOfText > $maxLength) {
      $charCountEl.parent().addClass('has-error');
      $charTextEl.text(' characters over the limit');
      $thisAria.text("Character limit has been reached, you must type fewer than " + $maxLength + " characters");
      if ($characterCount == 1) {
        $charTextEl.text(' character over the limit');
      } else {
        $charTextEl.text(' characters over the limit');
      }
    } else {
      $charCountEl.parent().removeClass('has-error');
      $charTextEl.text(' characters remaining');
      $thisAria.text("");
      if ($characterCount == 1) {
        $charTextEl.text(' character remaining');
      } else {
        $charTextEl.text(' characters remaining');
      }
    }
  }

  //----------Details > Summary ARIA

  $('[aria-expanded]').on('click', function() {
    var $this = $(this),
        $controls = $(this).attr('aria-controls');

    if(!$this.parent().hasClass('selected')) {
      if($this.is('[aria-expanded="false"]')) {
        $('#' + $controls).attr('aria-hidden', false);
        $this.attr('aria-expanded', true);
      } else {
        $('#' + $controls).attr('aria-hidden', true);
        $this.attr('aria-expanded', false);
      }
    }

  });

  $('[aria-hidden]').each(function() {
    var $controlID = $(this).attr('id');

    if($(this).is(':visible')) {
      $(this).attr('aria-hidden', false);
      $('[aria-controls="' + $controlID + '"]').attr('aria-expanded', true);
    }
  });

  //------- Select to inject content to text input

  $('.select-inject').on('change', function () {
    var $this = $(this),
        $selectedOption = $this.find('option:selected'),
        $thisOptionText = $selectedOption.text(),
        $theInput = $this.closest('.form-group').find('.select-injected'),
        $selectedVal = $selectedOption.val();

    $theInput.val($thisOptionText);

    $('.selfServe').each(function() {
      if($(this).prop('id') == $selectedVal) {
        $(this).show();
        $('.selfServe').not($(this)).hide();
      }
    });

    if($('#' + $selectedVal).length == 0) {
      $('.selfServe').hide();
    }

    if ($selectedVal == "noSelect") {
      $theInput.val("");
    }

    $theInput.focusout();
  });

  //------- Password meter

  if($('.new-password').length) {
    var minChars = 9,
        upperCase = new RegExp('[A-Z]'),
        lowerCase = new RegExp('[a-z]'),
        numbers = new RegExp('[0-9]'),
        passInput = $('.new-password'),
        confirmPass = $('.confirm-password'),
        requiresUppercase = $('#includesUppercase'),
        requiresLowercase = $('#includesLowercase'),
        requiresNumber = $('#includesNumber'),
        requires9Characters = $('#includes9Characters'),
        upperIcon = requiresUppercase.find('.the-icon'),
        lowerIcon = requiresLowercase.find('.the-icon'),
        numberIcon = requiresNumber.find('.the-icon'),
        char9Icon = requires9Characters.find('.the-icon');

    // passInput.after('<p class="form-hint text strength-indicator hide-nojs">Password validation: <span id="pass_meter"></span></p>');
    confirmPass.after('<div id="matchingHint" class="invisible"><p class="form-hint">&nbsp;<span id="pass_match" aria-live="polite"></span></p></div>');

    confirmPass.on('blur', function() {
      var currentConfirmPassword = $(this).val();
      var originalPassword = passInput.val();
      if(currentConfirmPassword || originalPassword) {
      $('#matchingHint').removeClass('invisible');
        if(currentConfirmPassword === originalPassword) {
          $('#pass_match').removeClass('strength-weak').addClass('strength-strong').html("<i class='fa fa-check'></i>Your passwords match");
        } else {
          $('#pass_match').removeClass('strength-strong').addClass('strength-weak').html("<i class='fa fa-times'></i>Your passwords don't match");
        }
      }
      if($('#pass_match').hasClass('strength-weak') || $('#passwordRequirements li').hasClass('strength-weak')) {
        $('#errorPassword').removeClass('hidden');
      } else {
        $('#errorPassword').addClass('hidden');
      }
    });

    passInput.keyup(function () {
      var passVal = $(this).val(),
          passMeter = $("#pass_meter"),
          matchVal = confirmPass.val();

      if(passVal.match(upperCase)) {
        requiresUppercase.addClass('strength-strong');
        upperIcon.removeClass('fa-minus fa-times');
        upperIcon.addClass('fa-check');
      } else {
        requiresUppercase.removeClass('strength-strong strength-weak');
        upperIcon.removeClass('fa-check');
        upperIcon.addClass('fa-minus');
      }

      if(passVal.match(lowerCase)) {
        requiresLowercase.addClass('strength-strong');
        lowerIcon.removeClass('fa-minus fa-times');
        lowerIcon.addClass('fa-check');
      } else {
        requiresLowercase.removeClass('strength-strong strength-weak');
        lowerIcon.removeClass('fa-check');
        lowerIcon.addClass('fa-minus');
      }

      if(passVal.match(numbers)) {
        requiresNumber.addClass('strength-strong');
        numberIcon.removeClass('fa-minus fa-times');
        numberIcon.addClass('fa-check');
      } else {
        requiresNumber.removeClass('strength-strong strength-weak');
        numberIcon.removeClass('fa-check');
        numberIcon.addClass('fa-minus');
      }

      if(passVal.length >= minChars) {
        requires9Characters.addClass('strength-strong');
        char9Icon.removeClass('fa-minus fa-times');
        char9Icon.addClass('fa-check');
      } else {
        requires9Characters.removeClass('strength-strong strength-weak');
        char9Icon.removeClass('fa-check');
        char9Icon.addClass('fa-minus');
      }

      if(matchVal.length >= minChars) {
        if(matchVal.length == passVal.length) {
          if(matchVal === passVal) {
            $('#pass_match').removeClass('strength-weak').addClass('strength-strong').html("<i class='fa fa-check'></i>Your passwords match");
          } else {
            $('#pass_match').removeClass('strength-strong').addClass('strength-weak').html("<i class='fa fa-times'></i>Your passwords don't match");
          }
        } else {
          $('#pass_match').removeClass('strength-strong').addClass('strength-weak').html("<i class='fa fa-times'></i>Your passwords don't match");
        }
      }

      if(!$('#pass_match').hasClass('strength-weak') || !$('#passwordRequirements li').hasClass('strength-weak')) {
        $('#errorPassword').addClass('hidden');
      }
    });

    passInput.on('blur', function() {
      $('#passwordRequirements li').each(function() {
        if(!$(this).hasClass('strength-strong')) {
          $(this).addClass('strength-weak').find('.the-icon').removeClass('fa-minus').addClass('fa-times');
        }
      });
    });

    confirmPass.keyup(function() {
      var passVal = passInput.val(),
          matchVal = $(this).val();

      if(matchVal.length == passVal.length) {
        if(matchVal === passVal) {
          $('#pass_match').removeClass('strength-weak').addClass('strength-strong').html("<i class='fa fa-check'></i>Your passwords match");
        } else {
          $('#pass_match').removeClass('strength-strong').addClass('strength-weak').html("<i class='fa fa-times'></i>Your passwords don't match");
        }
      } else if(matchVal.length !== 0 ) {
        $('#pass_match').removeClass('strength-strong').addClass('strength-weak').html("<i class='fa fa-times'></i>Your passwords don't match");
        }

    });
  }

  //------- Set personal details

  $('#addressManualLink').on('click', function(e) {
    e.preventDefault();

    $('#addressManualInput').removeClass('disabled');
    $('#address\\.line1').focus();
  });

  $('#outsideUk').on('change', function() {

    $('#address\\.line1').val("");
    $('#address_line2').val("");
    $('#address_line3').val("");
    $('#address_line4').val("");
    $('#postCode').val("");
    $('#country').val("");

    if($(this).is(':checked')) {
      $('#addressManualInput').removeClass('disabled');
      $('#postCode').closest('.form-group').addClass('toggle-content');
      $('#country').closest('.form-group').removeClass('toggle-content');
      $('#address\\.line1').focus();
      } else {
      $('#postCode').closest('.form-group').removeClass('toggle-content');
      $('#country').closest('.form-group').addClass('toggle-content');
      }
    });

  //------- Inline details toggle

  $('.summary-style').on('click', function() {
    $this = $(this);

    $this.toggleClass('open');

    $this.next('.detail-content').toggle();
  });

  // if($('html').hasClass('no-touch')) {
  //   $('.chosen-select').chosen({width: '100%'});
  // } else {
  //   $('.chosen-select').each(function() {
  //     $(this).find('.placeholder-option').text('Select an option')
  //   });
  // }

});;$(function() {
  if($('#choosePrefLocFramHeading').length ) {
    var $selectedRegion = '',
        $selectedRegionName = '',
        $firstSchemeVal = $('#schemePref1').attr('data-scheme'),
        $secondSchemeVal = $('#schemePref2').attr('data-scheme');

    if(!$('.map-legend-container').hasClass('disabled') && !$('#regionSelect option[selected]').length) {
      // Animate map after 2 seconds
      setTimeout(function() {
        $('.svg-map-container').addClass('hvr-back-pulse');
        $('.map-legend').show();
        $('#hoveredRegionName').text('Choose a region to filter the locations');
      }, 1000);

      // Stop animation and remove hint
      setTimeout(function() {
        $('.svg-map-container').removeClass('hvr-back-pulse');
        $('.map-legend').fadeOut('slow');
        $('#chooseRegionContainer').fadeIn('slow');
      }, 3000);
    }

    // On hover highlight the region on the map and show region name
    $('.region-container').not($selectedRegion).hover(function() {
      var $this = $(this),
          $regionName = $this.attr('id');

      $('.map-legend').show().removeClass('disabled');
      $('#hoveredRegionName').text($regionName);

      $('.svg-map-container').removeClass('hvr-back-pulse');
      $('#chooseRegionContainer').show();

    }, function() {
      if($selectedRegionName != '') {
        $('#hoveredRegionName').text($selectedRegionName);
      }
    });

    // Remove the legend if no region selected
    $('.svg-map').on('mouseleave', function() {
      if($selectedRegionName == '') {
        $('.map-legend').fadeOut();
      } else {
        $('.map-legend').addClass('disabled');
      }
    });

    // Clicking region will fire the region selection panel
    $('.region-container').on('click', function(e) {
      var $this = $(this),
          regionName = $this.attr('id');
      regionObject = availableLocationsAndSchemes[regionName];

      var locationsInRegion = $.map(regionObject, function(value, key) {
        return key;
      });

      $selectedRegion = $this;

      // IE8 Link behaviour
      e.preventDefault();

      $('#regionSelect').html('<option value="null">-- Choose a location in ' + regionName + ' --</option>')
          .append('<optgroup id="regionOptGroup" class="' + regionName + '" data-optregion="' + regionName + '" label="' + regionName + '"/>');

      $.each(locationsInRegion, function(i) {
        locationName = locationsInRegion[i];

        $('#regionOptGroup').append('<option value="' + regionName + ';' + locationName +'">' + locationName + '</option>');

      });

      $('#regionOptGroup').find('[value="'+ disabledLocation +'"]').attr('disabled', 'disabled');

      $('#selectLocationBlurb, #selectSecondLocationBlurb, #locationSelectedText, #locationSelectedContainer, #choiceSave')
          .addClass('toggle-content').attr('aria-hidden', true);

      $("#schemePref1 option, #schemePref2 option").attr('selected', false);

      $('#chosenRegionBlurb')
          .removeClass('toggle-content')
          .attr('aria-hidden', false)
          .find('b').text(regionName);

      $('#listOfLocationsContainer').removeClass('toggle-content');

      $this.attr('class', 'region-container selected-region');

      $('#hoveredRegionName').text(regionName);
      $selectedRegionName = regionName;

      $('.region-container')
          .not($selectedRegion)
          .attr('class', 'region-container');

      if($(window).width() <= 650) {
        scrollToTop();
      }

      setTimeout(function() {
        $('#regionSelect').focus();
      }, 200);

      $('#chooseRegionText').hide();
      $('#clearMap').show();

    });

    $('#viewListOfLocations').on('click', function(e) {
      $('#listOfLocationsContainer').removeClass('toggle-content');

      e.preventDefault();

    });

    function scrollToTop() {
      $('html, body').animate({
        scrollTop: $("#containingGridPreference").offset().top - 20
      }, 1000);
    }

    function hideBlurb() {
      $('#locationSelectedContainer').removeClass('toggle-content').attr('aria-hidden', false);

      $('.map-control').hide();

      $('#chosenRegionBlurb, #selectLocationBlurb').addClass('toggle-content').attr('aria-hidden', true);
      $('#locationSelectedText, #choiceSave').removeClass('toggle-content').attr('aria-hidden', false);

    }

    if($('#regionSelect option[selected]').length) {
      $('#listOfLocationsContainer, #choiceSave').removeClass('toggle-content').attr('aria-hidden', false);
      hideBlurb();
    }

    // Choosing a location affects schemes
    $("#regionSelect").on('change', function() {
      if($(this).val() != "null"){
        var selectedLocation = $(this).val(),
            selectedRegion = $(this).find('option:selected').parent().attr('data-optregion'),
            schemesInLocation = availableLocationsAndSchemes[selectedRegion][selectedLocation.split(";")[1]],
            schemeOptionsAvailable = '<option value="" class="placeholder-option">Choose a scheme</option>'

        //if first option is no longer available reset both scheme options
        if($.inArray($firstSchemeVal, schemesInLocation) == -1){
          $firstSchemeVal = "";
          $secondSchemeVal = "";
        }



        //disable second selection when there is only 1 scheme
        var disabledSecond = false;
        if(schemesInLocation.length == 1){
          disabledSecond = true
        }
        $('#schemePref2').prop('disabled', disabledSecond);

        hideBlurb();

        $.each(schemesInLocation, function(i) {
          var schemeName = schemesInLocation[i];

          schemeOptionsAvailable += '<option value="' + schemeName + '">' + schemeName + '</option>';
        });

        $('#schemePref1').html(schemeOptionsAvailable);
        $('#schemePref1 option[value= "'+ $firstSchemeVal +'"]').attr('selected', true);
        $('#schemePref1 option[value= "'+ $secondSchemeVal +'"]').attr('disabled', true);

        $('#schemePref2').html(schemeOptionsAvailable + '<option value="">No second preference</option>');
        if($secondSchemeVal !== "") {
          $('#schemePref2 option[value= "'+ $secondSchemeVal +'"]').attr('selected', true);
        }
        if($firstSchemeVal !== "") {
          $('#schemePref2 option[value= "'+ $firstSchemeVal +'"]').attr('disabled', true);
        }
      } else {
        $('#selectLocationBlurb, #selectSecondLocationBlurb, #locationSelectedText, #locationSelectedContainer, #choiceSave')
            .addClass('toggle-content').attr('aria-hidden', true);

        $('#chosenRegionBlurb')
            .removeClass('toggle-content')
            .attr('aria-hidden', false)
            .find('b').text(regionName);
      }


    });

    if($('#schemePref1 option[selected]').length) {
      var $selectedPref1 = $('#schemePref1').val(),
          $selectedPref2 = $('#schemePref2').val();

      $('#schemePref1').find('option[value="' + $selectedPref2 + '"]').attr('disabled', true);
      $('#schemePref2').find('option[value="' + $selectedPref1 + '"]').attr('disabled', true);

    }

    if($("#regionSelect").val() != "null"){
      $("#regionSelect").trigger('change');
    }

    // Scheme preference 1
    $('#schemePref1').on('change', function() {
      var $thisVal = $(this).val();

      $firstSchemeVal = $thisVal;

      $('#schemePref2').find('option[value="' + $thisVal + '"]').attr('disabled', true);
      $('#schemePref2').find('option').not('option[value="' + $thisVal + '"]').attr('disabled', false);


      if($('#schemePref2').val() !== '' || $('#schemePref2').attr('disabled') == 'disabled') {
        $('#choiceSave').removeClass('toggle-content');
      }
    });

    // Scheme preference 2
    $('#schemePref2').on('change', function() {
      var $thisVal = $(this).val();

      $secondSchemeVal = $thisVal;

      $('#schemePref1').find('option[value="' + $thisVal + '"]').attr('disabled', true);
      $('#schemePref1').find('option').not('option[value="' + $thisVal + '"]').attr('disabled', false);


      if($('#schemePref1').val() !== '') {
        $('#choiceSave').removeClass('toggle-content');
      }
    });


    $('#clearMap').on('click', function() {
      window.location.reload();
    });

  }

  //used on the 3rd questionnaire page
  $('.initiallyHidden').hide();

  var contentHide = $('.showsContent');

  contentHide.each(function(index) {
    if($(this).parent().hasClass("selected")) {
      $('.hidingContent').show();
    }
  });

  var occupationHide = $('.showsOccupations');

  occupationHide.each(function(index) {
    if($(this).parent().hasClass("selected")) {
      $('.hidingOccupations').show();
    }
  });

  $('.hidesContent').on('change', function(){
    $('.hidingContent').hide();
    $('.hidingContent').find(":checkbox").attr('checked', false);
    $('.hidingContent').find('select').val("");
    $('.hidingContent').find('select').attr('disabled', false);
  });

  $('.showsContent').on('change', function(){
    $('.hidingContent').show()
  });

  $('.hidesOccupations').on('change', function(){
    $('.hidingOccupations').hide();
    $('.hidingOccupations').find(":checkbox").attr('checked', false);
    $('.hidingOccupations').find('select').val("");
    $('.hidingOccupations').find('select').attr('disabled', false);
  });

  $('.showsOccupations').on('change', function(){
    $('.hidingOccupations').show()
  });
});;$(function() {

  //-- Faking details behaviour

  $('.no-details').on('click keypress', 'summary', function(e) {
    var $this = $(this);
    if (e.which === 13 || e.type === 'click') {
      $this.parent().toggleClass('open');
    }
  });

  // Accessibility fixes

  $('input, select').not('[optional], [data-optional] *, [type="hidden"]').attr('required', true);

  $('#password').attr('aria-labelledby', 'firstPassLabel hiddenPasswordRequirements');

  $('label[for="address_line2"]').text('Address line 2').addClass('visuallyhidden');
  $('label[for="address_line3"]').text('Address line 3').addClass('visuallyhidden');
  $('label[for="address_line4"]').text('Address line 4').addClass('visuallyhidden');

  if($('#civilServantQuestion').length) {
    var $civilServantSection = $('#civilServantQuestion'),
        legendText = $civilServantSection.find('h2').text();

    $civilServantSection.find('fieldset:eq(0)').prepend('<legend class="visuallyhidden">' + legendText + '</legend>');
  }

  if($('.editSection').length) {
    $('.editSection').each(function() {
      var sectionName = $(this).closest('h2').find('.sectionTitle').text();

      $(this).text(sectionName);
});
  }

  $('#printLink').on('click', function(e) {
    e.preventDefault();

    window.print();
  });

  function addSchoolsDropdown(selector, idSelector) {
    $(selector).keypress(function(){
      $(idSelector).val("");
    });
    $(selector).autocomplete({
      source: function( request, response ) {
        var r = jsRoutes.controllers.SchoolsController.getSchools(request.term)
        r.ajax({
          success : function(data) {
            $(selector).removeClass('ui-autocomplete-loading');

            response( $.map( data, function(item) {
              item.label = item.label
              item.value = item.name
              return item
            }));
          },
          error: function(data) {
            $(selector).removeClass('ui-autocomplete-loading');
          }
        });
      },
      minLength: 3,
      change: function(event, ui) {},
      open: function() {},
      close: function() {},
      focus: function(event,ui) {},
      select: function(event, ui) {
        $(idSelector).val(ui.item.id);
      },
      create: function () {
        $(this).data("ui-autocomplete")._renderItem = function (ul, item) {
          if(item.value ==''){
            return $('<li class="ui-menu-item ui-state-disabled">&nbsp;&nbsp;'+item.label+'</li>').appendTo(ul);
          }else{
            return $("<li>")
                .append("<a>" + item.label + "</a>")
                .appendTo(ul);
          }
        };
      }
    });
  }

  $('#schoolName14to16').ready(function() {
    addSchoolsDropdown("#schoolName14to16", "#schoolId14to16");
  });
  $('#schoolName16to18').ready(function() {
    addSchoolsDropdown("#schoolName16to18", "#schoolId16to18");
  });
});

$(function () {
  'use strict';

  var TIMES = [{
    message: '1 minute', // time left until expiry
    delay: 1740000 // 29 mins in milliseconds
  }, {
    message: 'RELOAD',
    delay: 70000
  }];

  var messageTimes = window.MODAL_TIMES || TIMES;
  var timerIndex = 0;
  var isShown = false;
  var timer = null;

  var $el = document.querySelector('#modal');
  var $yesButton = $el.querySelector('.button--modal-yes');
  var $noButton = $el.querySelector('.button--modal-no');
  var $timeLeft = $el.querySelector('.modal__time-left');
  var $htmlEl = document.querySelector('html');

  $($noButton).on('click', function(e) {
    e.preventDefault();
    e.stopPropagation();
    hide();
  });

  $($yesButton).on('click', function(e) {
    e.preventDefault();
    e.stopPropagation();
    hide();

    extendSession();
  });

  function hide() {
    $htmlEl.className = $htmlEl.className.replace(' show-modal', ' ');
    isShown = false;
  }

  function show() {
    if ($htmlEl.className.indexOf('show-modal') < 0) {
      $htmlEl.className += ' show-modal';
    }
    isShown = true;
  }

  function handleTimer() {
    if (timerIndex + 1 < messageTimes.length) {
      $timeLeft.innerText = messageTimes[timerIndex].message;
      show();

      startTimer(messageTimes[++timerIndex].delay);
    } else {
      location.reload(true);
    }
  }

  function startTimer(delay) {
    if (timer) {
      clearTimer(timer);
    }

    timer = window.setTimeout(handleTimer, delay);
  }

  function clearTimer(timer) {
    if (timer) {
      window.clearTimeout(timer);
    }
  }

  function extendSession() {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/fset-fast-stream/extendIdleTimeout', true);

    $(xhr).on('readystatechange', function() {
      if (xhr.readyState !== 4) {
        return;
      }

      if (xhr.status === 200) {
        timerIndex = 0;
        startTimer(messageTimes[timerIndex].delay);
      }
    });

    xhr.send();
  }

  startTimer(messageTimes[timerIndex].delay);
});
