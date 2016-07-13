$(function() {

  // Variable used to track if there are any outstanding actions that webdriver should wait for during
  // the acceptance test runs
  window.csrActive = 0

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

    $('html').addClass('mobile-browser');

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


  $('.menu-trigger').on('click', function() {
    $(this).next('.menu').toggleClass('menu-open');
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
        $disTarget = $this.parent().attr('data-distarget'),
        $theTargetControl = $('#' + $disTarget);

    $('input:not(:checked)').parent().removeClass('selected');
    $('input:checked').parent().addClass('selected');

    if($target == undefined) {
      $this.closest('.form-group').next('.toggle-content').hide().attr('aria-hidden', true);
      $this.closest('.form-group').find('[aria-expanded]').attr('aria-expanded', false);
      $this.closest('.form-group').next('.toggle-content').find('[data-requiredifshown] input').attr('required', false);
    } else {
      $('#' + $target).show();
      $('#' + $target).find('[data-requiredifshown] input').attr('required', true);

      if($this.closest('.form-group').hasClass('blocklabel-single')) {

        $this.closest('.blocklabel-single-container').find('.blocklabel-content').not('#' + $target).hide();
      }
    }

    if($disTarget && !$theTargetControl.attr('disabled')) {
      $theTargetControl.attr('disabled', true);
      if($theTargetControl.attr('type') == 'text') {
        $theTargetControl.val('');
      } else if($theTargetControl.is('select')) {
        $theTargetControl.find('> option:first-of-type').attr('selected', true).trigger("change");
      }
    } else if($disTarget && $theTargetControl.attr('disabled')) {
      $theTargetControl.attr('disabled', false);
    }

  });

  $('.selectWithOptionTrigger').on('change', function() {
    var optionTrigger = $(this).find('.optionTrigger'),
        optionTargetID = '#' + optionTrigger.attr('data-optiontrigger'),
        optionTarget = $(optionTargetID),
        optionTargetRequired = $(optionTargetID + '[data-requiredifshown]');

    if(optionTrigger.is(':selected')) {
      optionTarget.show();
      optionTargetRequired.find('input').attr('required', true);
    } else {
      optionTarget.find("input").val('');
      optionTarget.hide();
      optionTargetRequired.find('input').attr('required', false);
    }
  }).trigger("change");

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

  //--------Expanding tables

  $('.tbody-3rows').each(function() {
    var $this       = $(this),
        $rowLength  = $this.find('tr').length,
        $expandRows = $this.next('.tbody-expandrows'),
        $after3Rows = $this.find('tr:nth-of-type(3)').nextAll(),
        $after6Rows = $this.find('tr:nth-of-type(6)').nextAll();

    if($rowLength > 3 && !$this.hasClass('tbody-withReasons')) {
      $expandRows.show();
      $after3Rows.hide().attr('aria-hidden', true);
    } else if($rowLength > 6 && $this.hasClass('tbody-withReasons')) {
      $expandRows.show();
      $after6Rows.hide().attr('aria-hidden', true);
    }

  });

  $('.btnExpandRows').on('click', function() {
    var $this        = $(this),
        $tbodyExpand = $this.closest('.tbody-expandrows');
        $tbody3Rows   = $tbodyExpand.prev('.tbody-3rows').find('tr:nth-of-type(3)').nextAll(),
        $tbodyWithReasons  = $tbodyExpand.prev('.tbody-withReasons').find('tr:nth-of-type(6)').nextAll();


    if(!$tbodyExpand.prev('.tbody-withReasons').length > 0) {
      $tbody3Rows.toggle();
    } else if($tbodyExpand.prev('.tbody-withReasons').length > 0) {
      $tbodyWithReasons.toggle();
    }

    $this.closest('table').toggleClass('opened');

    if($this.text().indexOf('More') > -1) {
      $this.html('<i class="fa fa-angle-up"></i>Less');
      $this.attr('aria-expanded', false);
      if(!$tbodyExpand.prev('.tbody-withReasons').length > 0){
        $tbody3Rows.attr('aria-hidden', false);
      } else if ($tbodyExpand.prev('.tbody-withReasons').length > 0) {
        $tbodyWithReasons.attr('aria-hidden', false);
      }
    } else {
      $this.html('<i class="fa fa-angle-down"></i>More');
      $this.attr('aria-expanded', true);
      if(!$tbodyExpand.prev('.tbody-withReasons').length > 0){
        $tbody3Rows.attr('aria-hidden', true);
      } else if ($tbodyExpand.prev('.tbody-withReasons').length > 0) {
        $tbodyWithReasons.attr('aria-hidden', true);
      }
    }

    return false;

  });

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

  //----------Radio expanding lists IE8

  if($('html').hasClass('lt-ie9')) {
     $('.list-checkradio input[type=radio]:checked').siblings('details').addClass('ie8-details');

     $('.list-checkradio > li').on('click', function () {
       var $this = $(this),
           $thisDetails = $this.find('details');

       $('.list-checkradio input[type=radio]').not(':checked').siblings('details').removeClass('ie8-details');

       $thisDetails.addClass('ie8-details');

     });
   }

  //----------Tabbed content

  $('.tabbed-tab').attr('href', "#");

  $('.tabbed-tab').on('click', function() {
      var $this = $(this),
          $tabId = $this.attr('tab');

      $this.addClass('active');

      $('.tabbed-tab').not($('[tab="' + $tabId + '"]')).removeClass('active');

      if ($($tabId).length) {
          $($tabId).show();

          $('.tabbed-content').not($tabId).hide();
      } else {
          var $tabClass = '.' + $tabId.substr(1);

          $('.tabbed-element' + $tabClass).show();
          $('.tabbed-element').not($tabClass).hide();
      }

      return false;
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
        passInput = $(".new-password");

    passInput.after('<p class="form-hint text strength-indicator hide-nojs">Password validation: <span id="pass_meter" aria-live="polite"></span></p>');

    passInput.keyup(function () {
      var passVal = $(this).val(),
          passMeter = $("#pass_meter");

      passMeter.removeClass();

      if(passVal.length < minChars) {
        passMeter.addClass('strength-weak').text('Must be at least ' + minChars + ' characters');
      } else {
        if(!passVal.match(upperCase) && !passVal.match(lowerCase) && !passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires upper and lowercase letters and at least one number');
        }
        if(passVal.match(upperCase) && !passVal.match(lowerCase) && !passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires lowercase letters and at least one number');
        }
        if(!passVal.match(upperCase) && passVal.match(lowerCase) && !passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires uppercase letters and at least one number');
        }
        if(!passVal.match(upperCase) && !passVal.match(lowerCase) && passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires upper and lowercase letters');
        }
        if(!passVal.match(upperCase) && passVal.match(lowerCase) && passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires uppercase letters');
        }
        if(passVal.match(upperCase) && passVal.match(lowerCase) && !passVal.match(numbers)) {
          passMeter.addClass('strength-weak').text('Requires at least one number');
        }
        if(passVal.match(upperCase) && passVal.match(lowerCase) && passVal.match(numbers)) {
          passMeter.addClass('strength-strong').text('Your password is valid');
        }
      }

    });
  }

  //------- Inline details toggle

  $('.summary-style').on('click', function() {
    $this = $(this);

    $this.toggleClass('open');

    $this.next('.detail-content').toggle();
  });

});

$(function() {
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

    if (!$('html').hasClass('mobile-browser')) {
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
    } else {
      if($('.map-legend-container').hasClass('disabled')) {
        scrollToTop();
      }
    }

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

    $('#viewListOfLocations, #viewListOfLocations-hidden').on('click', function(e) {
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
      $('.hidingContent[data-requiredifshown]').find('input, select').attr('required', false);
  });

  $('.showsContent').on('change', function(){
      $('.hidingContent').show();
      $('.hidingContent[data-requiredifshown]').find('input, select').not('[data-optional] [data-optional] *').attr('required', true);
  });

  $('.hidesOccupations').on('change', function(){
    $('.hidingOccupations').hide();
    $('.hidingOccupations').find(":checkbox").attr('checked', false);
    $('.hidingOccupations').find('select').val("");
    $('.hidingOccupations').find('select').attr('disabled', false);
    $('.hidingOccupations[data-requiredifshown]').find('input, select').attr('required', false);
  });

  $('.showsOccupations').on('change', function(){
    $('.hidingOccupations').show();
    $('.hidingOccupations[data-requiredifshown]').find('input, select').not('[data-optional] [data-optional] *').attr('required', true);
  });

  $('input, select').not('[optional], [data-optional] *, [type="hidden"]').attr('required', true);
  // $('[data-optional]').find('input, select').attr('optional', true);

  $('#address_line1_field .form-label').html('Address <span class="visuallyhidden">line 1</span>');
  $('#address_line2').before('<label for="address_line2" class="visuallyhidden">Address line 2 (optional)</label>');
  $('#address_line3').before('<label for="address_line3" class="visuallyhidden">Address line 3 (optional)</label>');
  $('#address_line4').before('<label for="address_line4" class="visuallyhidden">Address line 4 (optional)</label>');

  $('.editSection').each(function() {
    var theSection = $(this).closest('.heading-large').find('.sectionTitle').text();

    $(this).text(theSection);
  });

});

;$(function() {

  //-- Faking details behaviour

  $('.no-details').on('click keypress', 'summary', function(e) {
    var $this = $(this);
    if (e.which === 13 || e.type === 'click') {
      $this.parent().toggleClass('open');
    }
  });

});