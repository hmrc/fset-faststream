/*
 * Copyright 2019 HM Revenue & Customs
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

package services.testdata.faker

import factories.UUIDFactory
import model.EvaluationResults.Result
import model.Exceptions.DataFakingException
import model._
import model.exchange.AssessorAvailability
import model.persisted.eventschedules.{ EventType, Session, _ }
import org.joda.time.{ LocalDate, LocalTime }
import repositories.{ SchemeRepository, SchemeYamlRepository }
import repositories.events.{ LocationsWithVenuesInMemoryRepository, LocationsWithVenuesRepository }

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object DataFaker extends DataFaker(SchemeYamlRepository)

//scalastyle:off number.of.methods
abstract class DataFaker(schemeRepo: SchemeRepository) {

  object ExchangeObjects {

    case class AvailableAssessmentSlot(venue: Venue, date: LocalDate, session: String)

  }

  object Random {
    def randOne[T](options: List[T], cannotBe: List[T] = Nil) = {
      val filtered = options.filterNot(cannotBe.contains)
      if (filtered.isEmpty) {
        throw DataFakingException(s"There were no items left after filtering.")
      } else {
        util.Random.shuffle(filtered).head
      }
    }

    def randList[T](options: List[T], size: Int, cannotBe: List[T] = Nil): List[T] = {
      if (size > 0) {

        val filtered = options.filterNot(cannotBe.contains)
        if (filtered.isEmpty) {
          throw DataFakingException(s"There were no items left after filtering.")
        } else {
          val newItem = util.Random.shuffle(filtered).head
          newItem :: randList(options, size - 1, newItem :: cannotBe)
        }
      } else {
        Nil
      }
    }

    // Purposefully always at least 2 and max of 4. SdipFaststream candidates actually can have 5 -
    // 4 selectable schemes plus Sdip, which is assigned automatically, but we just support 4 here
    def randNumberOfSchemes = {
      randOne(List(2, 3, 4))
    }

    def upperLetter: Char = randOne(( 'A' to 'Z' ).toList)

    def bool: Boolean = randOne(List(true, false))

    def boolTrue20percent: Boolean = randOne(List(1, 2, 3, 4, 5)) == 5

    def number(limit: Option[Int] = None): Int = util.Random.nextInt(limit.getOrElse(2000000000))

    def mediaReferrer = randOne(List(
      None,
      Some("GOV.UK or Civil Service Jobs"),
      Some("Recruitment website"),
      Some("Social Media (Facebook, Twitter or Instagram)"),
      Some("Fast Stream website (including scheme sites)"),
      Some("News article or online search (Google)"),
      Some("Friend in the Fast Stream"),
      Some("Friend or family in the Civil Service"),
      Some("Friend or family outside of the Civil Service"),
      Some("Careers fair (University or graduate)"),
      Some("University careers service (or jobs flyers)"),
      Some("University event (Guest lecture or skills session)"),
      Some("Other")
    ))

    def hasDisabilityDescription: String = randOne(List("I am too tall", "I am too good", "I get bored easily"))

    def onlineAdjustmentsDescription: String = randOne(List(
      "I am too sensitive to the light from screens",
      "I am allergic to electronic-magnetic waves",
      "I am a convicted cracker who was asked by the court to be away from computers for 5 years"))

    def assessmentCentreAdjustmentDescription: String = randOne(List(
      "I am very weak, I need constant support",
      "I need a comfortable chair because of my back problem",
      "I need to take a rest every 10 minutes"))

    def phoneAdjustmentsDescription: String = randOne(List(
      "I need a very loud speaker",
      "I need good headphones"
    ))

    def passmark: Result = randOne(List(EvaluationResults.Green, EvaluationResults.Amber, EvaluationResults.Red))

    /* TODO Fix these again once the event allocation features have been done

    private def venueHasFreeSlots(venue: Venue): Future[Option[AvailableAssessmentSlot]] = {
      applicationAssessmentRepository.applicationAssessmentsForVenue(venue.name).map { assessments =>
        val takenSlotsByDateAndSession = assessments.groupBy(slot => slot.date -> slot.session).map {
          case (date, numOfAssessments) => (date, numOfAssessments.length)
        }
        val assessmentsByDate = venue.capacityDates.map(_.date).toSet
        val availableDate = assessmentsByDate.toList.sortWith(_ isBefore _).flatMap { capacityDate =>
          List("AM", "PM").flatMap { possibleSession =>
            takenSlotsByDateAndSession.get(capacityDate -> possibleSession) match {
              // Date with no free slots
              case Some(slots) if slots >= 6 => None
              // Date with no assessments booked or Date with open slots (all dates have 6 slots per session)
              case _ => Some(AvailableAssessmentSlot(venue, capacityDate, possibleSession))
            }
          }
        }
        availableDate.headOption
      }
    }


    def region: Future[String] = {
      LocationsWithVenuesYamlRepository.locationsAndAssessmentCentreMapping.map { locationsToAssessmentCentres =>
        val locationToRegion = locationsToAssessmentCentres.values.filterNot(_.startsWith("TestAssessment"))
        randOne(locationToRegion.toList)
      }
    }

    def location(region: String, cannotBe: List[String] = Nil): Future[String] = {
      LocationsWithVenuesYamlRepository.locationsWithVenuesList.map { locationsToAssessmentCentres =>
        val locationsInRegion = locationsToAssessmentCentres.filter(_._2 == region).keys.toList
        randOne(locationsInRegion, cannotBe)
      }
    }
    */

    def schemeTypes = randList(schemeRepo.schemes.toList, randNumberOfSchemes)

    def gender = randOne(List(
      "Male",
      "Female",
      "Other",
      "I don't know/prefer not to say"))

    def sexualOrientation = randOne(List(
      "Heterosexual/straight",
      "Gay/lesbian",
      "Bisexual",
      "Other",
      "I don't know/prefer not to say"))

    def ethnicGroup = randOne(List(
      "English/Welsh/Scottish/Northern Irish/British",
      "Irish",
      "Gypsy or Irish Traveller",
      "Other White background",
      "White and Black Caribbean",
      "White and Black African",
      "White and Asian",
      "Other mixed/multiple ethnic background",
      "Indian",
      "Pakistani",
      "Bangladeshi",
      "Chinese",
      "Other Asian background",
      "African",
      "Caribbean",
      "Other Black/African/Caribbean background",
      "Arab",
      "Other ethnic group",
      "I don't know/prefer not to say"
    ))

    def age14to16School = randOne(List("Blue Bees School", "Green Goblins School", "Magenta Monkeys School", "Zany Zebras School"))

    def schoolType14to16 = randOne(List(
      "stateRunOrFunded-selective",
      "stateRunOrFunded-nonSelective",
      "indyOrFeePaying-bursary",
      "indyOrFeePaying-noBursary"
    ))


    def age16to18School = randOne(List("Advanced Skills School", "Extremely Advanced School", "A-Level Specialist School", "14 to 18 School"))

    // scalastyle:off method.length
    def university = randOne(List(
      ("Abingdon and Witney College", "A14-AWC"),
      ("University of Aberdeen", "A20-ABRDN"),
      ("University of Abertay Dundee", "A30-ABTAY"),
      ("Aberystwyth University", "A40-ABWTH"),
      ("ABI College", "A41-ABIC"),
      ("Access to Music", "A43-ACCM"),
      ("Accrington and Rossendale College", "A44-ARC"),
      ("College of Agriculture, Food and Rural Enterprise", "A45-CAFRE"),
      ("The Academy of Contemporary Music", "A48-ACM"),
      ("Amersham &amp; Wycombe College", "A55-AMWYC"),
      ("Amsterdam Fashion Academy", "A57-AFC"),
      ("Anglia Ruskin University", "A60-ARU"),
      ("Anglo-European College of Chiropractic", "A65-AECC"),
      ("The Arts University Bournemouth", "A66-AUCB"),
      ("Askham Bryan College", "A70-ABC"),
      ("Aston University", "A80-ASTON"),
      ("Bangor University", "B06-BANGR"),
      ("Barnet and Southgate College", "B08-BSC"),
      ("Barnfield College", "B09-BARNF"),
      ("Barking and Dagenham College", "B11-BARK"),
      ("Barnsley College", "B13-BARNF"),
      ("Basingstoke College of Technology", "B15-BCOT"),
      ("University of Bath", "B16-BATH"),
      ("Bicton College", "B18-BICOL"),
      ("Bath Spa University", "B20-BASPA"),
      ("Bath College", "B21-BATHC"),
      ("University of Bedfordshire", "B22-BED"),
      ("Bedford College", "B23-BEDF"),
      ("Birkbeck, University of London", "B24-BBK"),
      ("Birmingham City University", "B25-BCITY"),
      ("Birmingham Metropolitan College", "B30-BMET"),
      ("The University of Birmingham", "B32-BIRM"),
      ("University College Birmingham", "B35-BUCB"),
      ("Bexley College", "B36-BEXL"),
      ("Bishop Burton College", "B37-BISH"),
      ("Bishop Grosseteste University", "B38-BGU"),
      ("BIMM", "B39-BIMM"),
      ("Blackburn College", "B40-BLACL"),
      ("Blackpool and The Fylde College", "B41-BLACK"),
      ("Berkshire College of Agriculture", "B42-BCS"),
      ("University of Bolton", "B44-BOLTN"),
      ("Bolton College", "B46-BOLTC"),
      ("Bournville College", "B48-BOURN"),
      ("Bournemouth and Poole College", "B49-BPOOL"),
      ("Bournemouth University", "B50-BMTH"),
      ("BPP University", "B54-BPP"),
      ("University of Bradford", "B56-BRADF"),
      ("Bradford College", "B60-BRC"),
      ("Bridgend College", "B68-BDGC"),
      ("Bridgwater College", "B70-BRIDG"),
      ("University of Brighton", "B72-BRITN"),
      ("British Institute of Technology and E-commerce", "B73-BITE"),
      ("Brighton and Sussex Medical School", "B74-BSMS"),
      ("City College Brighton &amp; Hove", "B76-CCBH"),
      ("Bristol, City of Bristol College", "B77-BCBC"),
      ("University of Bristol", "B78-BRISL"),
      ("Bristol, University of the West of England", "B80-BUWE"),
      ("British College of Osteopathic Medicine", "B81-BCOM"),
      ("Brooklands College", "B83-BROOK"),
      ("Brunel University London", "B84-BRUNL"),
      ("British School of Osteopathy", "B87-BSO"),
      ("University of Buckingham", "B90-BUCK"),
      ("Brooksby Melton College", "B92-BROKS"),
      ("Bury College", "B93-BURY"),
      ("Buckinghamshire New University", "B94-BUCKS"),
      ("Bromley College of Further and Higher Education", "B97-BCFHE"),
      ("Calderdale College", "C02-CALD"),
      ("University of Cambridge", "C05-CAM"),
      ("Cambridge Education Group Limited", "C06-CSVPA"),
      ("Cambridge Regional College", "C09-CRC"),
      ("Canterbury Christ Church University", "C10-CANCC"),
      ("Canterbury College", "C12-CANT"),
      ("Capel Manor College", "C13-CAPMC"),
      ("Cardiff University", "C15-CARDF"),
      ("Cardiff Metropolitan University", "C20-CUWIC"),
      ("Coleg Sir Gar", "C22-CARM"),
      ("Carshalton College", "C24-CARC"),
      ("Central Bedfordshire College", "C27-CBED"),
      ("CCP Graduate School Ltd", "C28-CCP"),
      ("University Campus Scarborough", "C29-SCARB"),
      ("University of Central Lancashire (UCLan)", "C30-CLANC"),
      ("Central College Nottingham", "C32-CCNOT"),
      ("Central Film School London", "C34-CFSL"),
      ("The Royal Central School of Speech and Drama", "C35-CSSD"),
      ("City of Glasgow College", "C39-CGC"),
      ("University of Chester", "C55-CHSTR"),
      ("Chesterfield College", "C56-CHEST"),
      ("Chichester College", "C57-CHCOL"),
      ("University of Chichester", "C58-CHICH"),
      ("City University London", "C60-CITY"),
      ("City College Coventry", "C64-CCC"),
      ("City and Islington College", "C65-CIC"),
      ("City of Sunderland College", "C69-CSUND"),
      ("Cleveland College of Art and Design", "C71-CLEVE"),
      ("Cliff College", "C72-CLIFC"),
      ("City of London College", "C74-CLC"),
      ("Colchester Institute", "C75-CINST"),
      ("Cornwall College", "C78-CORN"),
      ("Courtauld Institute of Art (University of London", "C80-CRT"),
      ("City College Plymouth", "C83-CPLYM"),
      ("Coventry University", "C85-COVN"),
      ("Craven College", "C88-CRAV"),
      ("Creative Academy", "C89-CRA"),
      ("University Centre Croydon (Croydon College)", "C92-CROY"),
      ("University for the Creative Arts (UCA)", "C93-UCA"),
      ("City of Wolverhampton College", "C96-CWC"),
      ("University of Cumbria", "C99-IPC"),
      ("Dearne Valley College", "D22-DVC"),
      ("De Montfort University", "D26-DEM"),
      ("Derby College", "D38-DCOL"),
      ("University of Derby", "D39-DERBY"),
      ("Doncaster College", "D52-DONC"),
      ("Duchy College", "D55-DUCHY"),
      ("Dudley College of Technology", "D58-DUDL"),
      ("University of Dundee", "D65-DUND"),
      ("Durham University", "D86-DUR"),
      ("Ealing, Hammersmith and West London College", "E10-EHWL"),
      ("University of East Anglia", "E14-EANG"),
      ("University of East London", "E28-ELOND"),
      ("East Riding College", "E29-ERC"),
      ("Easton and Otley College (an Associate College of UEA)", "E30-EASTC"),
      ("East Surrey College", "E32-ESURR"),
      ("Edge Hotel School", "E41-EHS"),
      ("Edge Hill University", "E42-EHU"),
      ("University of Edinburgh", "E56-EDINB"),
      ("Edinburgh Napier University", "E59-ENAP"),
      ("University of Essex", "E70-ESSEX"),
      ("ESCP Europe Business School", "E79-ESCP"),
      ("European School of Osteopathy", "E80-ESO"),
      ("Exeter College", "E81-EXCO"),
      ("University of Exeter", "E84-EXETR"),
      ("Falmouth University", "F33-FAL"),
      ("Fareham College", "F55-FAREC"),
      ("University Centre Farnborough", "F66-FCOT"),
      ("Furness College", "F95-FURN"),
      ("Gateshead College", "G09-GATE"),
      ("University of Glasgow", "G28-GLASG"),
      ("Glasgow Caledonian University", "G42-GCU"),
      ("Glasgow School of Art", "G43-GSA"),
      ("Gloucestershire College", "G45-GCOL"),
      ("University of Gloucestershire", "G50-GLOS"),
      ("Glyndwr University", "G53-GLYND"),
      ("Goldsmiths, University of London", "G56-GOLD"),
      ("Gower College Swansea", "G59-GCOLS"),
      ("University of Greenwich", "G70-GREEN"),
      ("Greenwich School of Management (GSM London)", "G74-GSM"),
      ("University Centre Grimsby", "G80-GRIMC"),
      ("Guildford College", "G90-GUILD"),
      ("Hackney Community College", "H02-HCC"),
      ("Hadlow College", "H03-HADCO"),
      ("Halesowen College", "H04-HALES"),
      ("Harrogate Colleg", "H07-HARRC"),
      ("The College of Haringey, Enfield and North East London", "H08-CHEN"),
      ("Harrow College", "H11-HARCO"),
      ("Harper Adams University", "H12-HAUC"),
      ("Havering College of Further &amp; Higher Education", "H14-HAVC"),
      ("Hereford College of Arts", "H18-HERE"),
      ("Heart of Worcestershire College", "H19-HWC"),
      ("Henley College Coventry", "H21-HENC"),
      ("Hartpury University Centre", "H22-HARTP"),
      ("Heriot-Watt University, Edinburgh", "H24-HW"),
      ("University of Hertfordshire", "H36-HERTS"),
      ("Hertford Regional College", "H37-HRC"),
      ("Highbury College", "H39-HIGHC"),
      ("University of the Highlands and Islands", "H49-UHI"),
      ("Holy Cross Sixth Form College and University Centre", "H51-HCSFC"),
      ("Hopwood Hall College", "H54-HOPH"),
      ("University of Huddersfield", "H60-HUDDS"),
      ("Hugh Baird College", "H65-HBC"),
      ("University of Hull", "H72-HULL"),
      ("Hull College", "H73-HULLC"),
      ("Hull York Medical School", "H75-HYMS"),
      ("The Institute of Contemporary Music Performance", "I25-ICMP"),
      ("Istituto Marangoni London", "I35-INMAR"),
      ("Imperial College London", "I50-IMP"),
      ("ifs University College", "I55-IFSS"),
      ("Islamic College for Advanced Studies", "I60-ICAS"),
      ("John Ruskin College", "J75-JRC"),
      ("West Kent and Ashford College", "K01-KCOLL"),
      ("Keele University", "K12-KEELE"),
      ("Kensington and Chelsea College", "K14-KCC"),
      ("Kensington College of Business", "K15-KCB"),
      ("Kendall College", "K16-KEND"),
      ("Kogan Academy of Dramatic Arts", "K20-KOGAN"),
      ("University of Kent", "K24-KENT"),
      ("King's College London (University of London)", "K60-KCL"),
      ("Kingston College", "K83-KCOL"),
      ("Kingston University", "K84-KING"),
      ("Kingston Maurward College", "K85-KMC"),
      ("Kirklees College", "K90-KIRK"),
      ("KLC School of Design", "K95-KLC"),
      ("Knowsley Community College", "K96-KNOW"),
      ("Lewisham Southwark College", "L01-LSCO"),
      ("Lakes College - West Cumbria", "L05-LCWC"),
      ("Lancaster University", "L14-LANCR"),
      ("University of Law (incorporating College of Law)", "L17-LAW"),
      ("Leeds City College", "L21-LCCOL"),
      ("University of Leeds", "L23-LEEDS"),
      ("Leeds Trinity University", "L24-LETAS"),
      ("Leeds Beckett University", "L27-LBU"),
      ("Leeds College of Art", "L28-LAD"),
      ("Leeds College of Music (UCAS)", "L30-LCM"),
      ("Leeds College of Building", "L32-LCB"),
      ("University of Leicester", "L34-LEICR"),
      ("Leicester College", "L36-LCOLL"),
      ("University of Lincoln", "L39-LINCO"),
      ("University of Liverpool", "L41-LVRPL"),
      ("Lincoln College", "L42-LINCN"),
      ("The City of Liverpool College", "L43-COLC"),
      ("Liverpool Hope University", "L46-LHOPE"),
      ("The Liverpool Institute for Performing Arts", "L48-LIVIN"),
      ("Liverpool John Moores University (LJMU)", "L51-LJM"),
      ("Coleg Llandrillo", "L53-LLC"),
      ("UCK Limted", "L62-LCUCK"),
      ("ARU London", "L63-LCA"),
      ("London Metropolitan University", "L68-LONMT"),
      ("London School of Commerce", "L70-LSC"),
      ("London School of Economics and Political Science (University of London)", "L72-LSE"),
      ("London School of Business and Management", "L73-LSBM"),
      ("London South Bank University", "L75-LSBU"),
      ("London School of Marketing Limited", "L76-LSM"),
      ("Loughborough College", "L77-LOUGH"),
      ("Loughborough University", "L79-LBRO"),
      ("The Manchester College", "M10-MCOL"),
      ("University of Manchester", "M20-MANU"),
      ("Manchester Metropolitan University", "M40-MMU"),
      ("Medipathways College", "M61-MEDIP"),
      ("Coleg Menai", "M65-MENAI"),
      ("Met Film School", "M73-MFS"),
      ("Mid Cheshire College", "M77-MCCFE"),
      ("Middlesex University", "M80-MIDDX"),
      ("Mid-Kent College of Higher and Further Education", "M87-MKENT"),
      ("Mont Rose College", "M88-MRC"),
      ("Milton Keynes College", "M89-MKCOL"),
      ("Moulton College", "M93-MOULT"),
      ("Myerscough College", "M99-MYERS"),
      ("Nazarene Theological College", "N11-NAZ"),
      ("NPTC Group", "N13-NEATH"),
      ("University of Newcastle upon Tyne", "N21-NEWC"),
      ("Newcastle College", "N23-NCAST"),
      ("New College Durham", "N28-NCD"),
      ("New College Nottingham", "N30-NCN"),
      ("Newham College London", "N31-NHAM"),
      ("New College Stamford", "N33-NCS"),
      ("Newman University, Birmingham", "N36-NWMAN"),
      ("University of Northampton", "N38-NTON"),
      ("Norwich University of the Arts", "N39-NUA"),
      ("Northbrook College Sussex", "N41-NBRK"),
      ("North East Surrey College of Technology", "N49-NESCT"),
      ("New College Telford", "N51-NCT"),
      ("Norland Nursery Training College Limited", "N52-NNCT"),
      ("New College of the Humanities", "N53-NCHUM"),
      ("North Hertfordshire College", "N57-NHC"),
      ("North Lindsey College", "N64-NLIND"),
      ("Northumbria University", "N77-NORTH"),
      ("Northumberland College", "N78-NUMBC"),
      ("North Warwickshire and Hinckley College", "N79-NWHC"),
      ("Norton Radstoc", "N81-Colleg"),
      ("Norwich City College of Further and Higher Education", "N82-NCC"),
      ("University of Nottingham", "N84-NOTTM"),
      ("North Kent College", "N85-NKC"),
      ("Nottingham Trent University", "N91-NOTRE"),
      ("University Campus Oldham", "O10-UCO"),
      ("The Open University (OU)", "O11-8"),
      ("Oaklands College", "O12-OAK"),
      ("Activate Learning (Oxford, Reading, Banbury &amp; Bicester)", "O25-OXCH"),
      ("Oxford University", "O33-OXF"),
      ("Oxford Brookes University", "O66-OXFD"),
      ("University of London Institute in Paris", "P26-PARIS"),
      ("Pearson College London (including Escape Studios)", "P34-PEARS"),
      ("Pembrokeshire College", "P35-PEMB"),
      ("Petroc", "P51-PETRO"),
      ("Peter Symonds College", "P52-PSC"),
      ("University Centre Peterborough", "P56-PETER"),
      ("Plumpton College", "P59-PLUMN"),
      ("Plymouth University", "P60-PLUMN"),
      ("University of St Mark and St John", "P63-PMARJ"),
      ("Plymouth College of Art", "P65-PCAD"),
      ("Point Blank Music School", "P73-POINT"),
      ("University of Portsmouth", "P80-PORT"),
      ("Queen Margaret University, Edinburgh", "Q25-QMU"),
      ("Queen Mary University of London", "Q50-QMUL"),
      ("The Queen's University Belfast", "Q75-QBELF"),
      ("Ravensbourne", "R06-RAVEN"),
      ("University of Reading", "R12-READG"),
      ("Reaseheath College", "R14-RHC"),
      ("Regent's University London", "R18-RBS"),
      ("Richmond, the American International University", "R20-RICH"),
      ("Arden University (RDI)", "R25-RDINT"),
      ("Robert Gordon University", "R36-RGU"),
      ("Roehampton University", "R48-ROE"),
      ("Rose Bruford College", "R51-ROSE"),
      ("Rotherham College of Arts and Technology", "R52-RCAT"),
      ("Royal Agricultural University, Cirencester", "R54-RAU"),
      ("Royal Academy of Dance", "R55-RAD"),
      ("Royal Holloway, University of London", "R72-RHUL"),
      ("Royal Veterinary College (University of London)", "R84-RVET"),
      ("Royal Welsh College of Music and Drama (Coleg Brenhinol Cerdd a Drama Cymru)", "R86-RWCMD"),
      ("Runshaw College", "R88-RUNSH"),
      ("Ruskin College, Oxford", "R90-RUSKC"),
      ("SRUC", "S01-SRUC"),
      ("University of Salford", "S03-SALF"),
      ("SAE Institute", "S05-SAE"),
      ("Selby College", "S06-SELBY"),
      ("Sandwell College", "S08-SAND"),
      ("SOAS (University of London)", "S09-SOAS"),
      ("Salford City College", "S11-SALCC"),
      ("University of Sheffield", "S18-SHEFD"),
      ("St Patrick's International College", "S19-SPIC"),
      ("South &amp; City College Birmingham", "S20-SCCB"),
      ("Sheffield Hallam University", "S21-SHU"),
      ("Sheffield College", "S22-SCOLL"),
      ("Shrewsbury College of Arts and Technology", "S23-SHREW"),
      ("Solihull College &amp; University Centre", "S26-SOLI"),
      ("University of Southampton", "S27-SOTON"),
      ("Somerset College", "S28-SOMER"),
      ("Southampton Solent University", "S30-SOLNT"),
      ("South Devon College", "S32-SDEV"),
      ("Sparsholt College", "S34-SPAR"),
      ("Southport College", "S35-SOCO"),
      ("University of St Andrews", "S36-STA"),
      ("South Cheshire College", "S41-SCC"),
      ("South Downs College", "S42-SDC"),
      ("South Essex College, University Centre Southend and Thurrock", "S43-SEEC"),
      ("South Leicestershire College", "S48-SLC"),
      ("St George's, University of London", "S49-SGEO"),
      ("South Thames College", "S50-SRUC"),
      ("University Centre St Helens", "S51-STHEL"),
      ("South Tyneside College", "S52-STYNE"),
      ("South Gloucestershire and Stroud College", "S55-SGSC"),
      ("Spurgeon's College", "S57-SPUR"),
      ("St. Mary's College", "S62-Blackbur"),
      ("St Mary's University, Twickenham, London", "S64-SMARY"),
      ("Southampton City College", "S67-SCCOL"),
      ("Staffordshire University", "S72-STAFF"),
      ("Stratford upon Avon College", "S74-STUAC"),
      ("University of Stirling", "S75-STIRL"),
      ("Stockport College", "S76-STOCK"),
      ("University of Strathclyde", "S78-STRAT"),
      ("Stranmillis University College: A College of Queen's University Belfast", "S79-SUCB"),
      ("University Campus Suffolk", "S82-UCS"),
      ("Sussex Coast College Hastings", "S83-SCCH"),
      ("University of Sunderland", "S84-SUND"),
      ("University of Surrey", "S85-SURR"),
      ("Sussex Downs College", "S89-SDCOL"),
      ("University of Sussex", "S90-SUSX"),
      ("Swansea University", "S93-SWAN"),
      ("University of Wales Trinity Saint David (UWTSD Swansea)", "S96-SMU"),
      ("Swindon College", "S98-SWIN"),
      ("Tameside College", "T10-TAMES"),
      ("BIMM London", "T15-TMS"),
      ("Teesside University", "T20-TEES"),
      ("Tottenham Hotspur Foundation", "T60-THF"),
      ("University of Wales Trinity Saint David (UWTSD Carmarthen / Lampeter)", "T80-UWTSD"),
      ("Truro and Penwith College", "T85-TRURO"),
      ("Tyne Metropolitan College", "T90-TMC"),
      ("UCFB", "U10-UCFB"),
      ("UK College of Business &amp; Computing", "U15-UKCBC"),
      ("University of Ulster", "U20-ULS"),
      ("University of the West of Scotland", "U40-UWS"),
      ("University of the Arts London", "U65-UAL"),
      ("UCL (University College London)", "U80-UCL"),
      ("Uxbridge College", "U95-UXBC"),
      ("Seevic College", "V03-SEEV"),
      ("University of South Wales", "W01-USW"),
      ("University of West London", "W05-UWL"),
      ("Wakefield College", "W08-WAKEC"),
      ("Walsall College", "W12-WALLS"),
      ("Warrington Collegiate", "W17-WARR"),
      ("University of Warwick", "W20-WARWK"),
      ("Warwickshire College", "W25-WARKS"),
      ("College of West Anglia", "W35-WESTA"),
      ("West Cheshire College", "W36-WCC"),
      ("West Herts College, Watford Associate College of University of Hertfordshire", "W40-WHWC"),
      ("West Lancashire College", "W42-WLC"),
      ("Weston College", "W47-WSTON"),
      ("University of Westminster", "W50-WEST"),
      ("City of Westminster College", "W51-WESTCL"),
      ("Westminster Kingsway College", "W52-WESTCL"),
      ("West Thames College", "W65-WTC"),
      ("Weymouth College", "W66-WEYC"),
      ("Wigan and Leigh College", "W67-WIGAN"),
      ("Wirral Metropolitan College", "W73-WMC"),
      ("Wiltshire College", "W74-WILT"),
      ("University of Wolverhampton", "W75-WOLVN"),
      ("University of Winchester", "W76-WIN"),
      ("University of Worcester", "W80-WORCS"),
      ("Writtle College", "W85-WRITL"),
      ("Yeovil College", "Y25-YEOV"),
      ("The University of York", "Y50-YORK"),
      ("York College (York)", "Y70-YCOLL"),
      ("York St John University", "Y75-YSJ"),
      ("Other", "Others")))

    // scalastyle:on method.length

    def degreeCategory = randOne(List(
      ("Combined", "(J)"),
      ("Agriculture & Related Subjects", "(5)"),
      ("Architecture, Building & Planning", "(A)"),
      ("Biological Sciences", "(3)"),
      ("Business & Administrative Studies", "(D)"),
      ("Creative Arts & Design", "(H)"),
      ("Computer Science", "(8)"),
      ("Education", "(I)"),
      ("Engineering & Technology", "(9)"),
      ("Humanities", "(G)"),
      ("Languages", "(F)"),
      ("Law", "(C)"),
      ("Librarianship & Information Science", "(E)"),
      ("Mathematical Science", "(7)"),
      ("Medicine & Dentistry", "(1)"),
      ("Subjects Allied to Medicine", "(2)"),
      ("Physical Science", "(6)"),
      ("Social, Economic & Political Studies", "(B)"),
      ("Veterinary Sciences", "(4)")))

    def homePostcode = randOne(List("AB1 2CD", "BC11 4DE", "CD6 2EF", "DE2F 1GH", "I don't know/prefer not to say"))

    def yesNo = randOne(List("Yes", "No"))

    def yesNoPreferNotToSay = randOne(List("Yes", "No", "I don't know/prefer not to say"))

    def employeeOrSelf = randOne(List(
      "Employee",
      "Self-employed with employees",
      "Self-employed/freelancer without employees",
      "I don't know/prefer not to say"))

    def sizeOfPlaceOfWork = randOne(List("Small (1 - 24 employees)", "Large (over 24 employees)"))

    def parentsDegree = randOne(List(
      "Degree level qualification",
      "Qualifications below degree level",
      "No formal qualifications",
      "I don't know/prefer not to say"
    ))

    def parentsOccupation = randOne(List(
      "Unemployed but seeking work",
      "Unemployed",
      "Employed",
      "Unknown"
    ))

    def skills: List[String] = randList(List(
      SkillType.SIFTER.toString,
      SkillType.QUALITY_ASSURANCE_COORDINATOR.toString,
      SkillType.ASSESSOR.toString,
      SkillType.CHAIR.toString), 4)

    def sifterSchemes: List[SchemeId] = randList(List(SchemeId("GovernmentEconomicsService"), SchemeId("ProjectDelivery"), SchemeId("Sdip")), 3)

    def parentsOccupationDetails: String = randOne(List(
      "Modern professional",
      "Clerical (office work) and intermediate",
      "Senior managers and administrators",
      "Technical and craft",
      "Semi-routine manual and service",
      "Routine manual and service",
      "Middle or junior managers",
      "Traditional professional"
    ))

    def sizeParentsEmployeer: String = randOne(List(
      "Small (1 to 24 employees)",
      "Large (over 24 employees)",
      "I don't know/prefer not to say"
    ))

    def getFirstname(userNumber: Int): String = {
      val firstName = randOne(Firstnames.list)
      s"$firstName$userNumber"
    }

    def getLastname(userNumber: Int): String = {
      val lastName = randOne(Lastnames.list)
      s"$lastName$userNumber"
    }

    def postCode: String = {
      s"${Random.upperLetter}${Random.upperLetter}1 2${Random.upperLetter}${Random.upperLetter}"
    }

    def accessCode: String = randomAlphaString(7)

    def getVideoInterviewScore: Double = randOne(List(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0))

    private def randomAlphaString(n: Int) = {
      val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
      Stream.continually(util.Random.nextInt(alphabet.length)).map(alphabet).take(n).mkString
    }

    val videoInterviewFeedback: String = "Collaborating and Partnering/In the interview you:\n" +
      "1. Provided limited specific, but some evidence of engagement.\n" +
      "2. Provided some limited evidence of focus on learning but none on personal development."

    object Assessor {
      private def location = randOne(List("London", "Newcastle"))

      def availability: Option[Set[AssessorAvailability]] = {
        if (boolTrue20percent) {
          Some(Set.empty)
        } else {
          val dates = ( 15 to 25 ).map(i => LocalDate.parse(s"2017-06-$i")).toSet
          Option(dates.flatMap { date =>
            if (bool) {
              Some(AssessorAvailability(location, date))
            } else {
              None
            }
          })
        }
      }
    }

    object Event {



      def id: String = UUIDFactory.generateUUID()

      import EventType._
      def eventType: EventType.Value = randOne(EventType.options.keys.toList)

      def description(eventType: EventType): String = eventType match {
        case EventType.FSB => randOne(ExternalSources.allFsbTypes.toList).key
        case _ => ""
      }

      def location(description: String): Location = {
        if (description.contains("interview")) {
          Location("Virtual")
        } else {
          randOne(List(Location("London"), Location("Newcastle")))
        }
      }

      def venue(l: Location): Venue = randOne(ExternalSources.venuesByLocation(l.name))

      def date: LocalDate = LocalDate.now().plusDays(number(Option(300)))

      def capacity: Int = randOne(List(8, 10, 12, 14, 16, 18))

      def minViableAttendees: Int = capacity - randOne(List(5, 3, 4, 2))

      def attendeeSafetyMargin: Int = randOne(List(1, 2, 3))

      def startTime: LocalTime = LocalTime.parse(randOne(List("9:30", "11:00", "13:30", "15:00")))

      def endTime: LocalTime = startTime.plusHours(1)

      def skillRequirements: Map[String, Int] = {
        val skills = SkillType.values.toList.map(_.toString)
        val numberOfSkills = randOne(( 1 to SkillType.values.size ).map(i => i).toList)
        val skillsSelected = randList(skills, numberOfSkills)

        def numberOfPeopleWithSkillsRequired = randOne(List(1, 2, 3, 4, 8))

        skillsSelected.map { skillSelected =>
          skillSelected -> numberOfPeopleWithSkillsRequired
        }.toMap
      }

      def sessions = randList(List(
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "First session",
          capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(1)),
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Advanced session",
          capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(2)),
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Midday session",
          capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(3)),
        Session(UniqueIdentifier.randomUniqueIdentifier.toString(), "Small session",
          capacity, minViableAttendees, attendeeSafetyMargin, startTime, startTime.plusHours(4))
      ), 2)
    }

    object Allocation {
      def status: AllocationStatuses.Value = Random.randOne(List(AllocationStatuses.CONFIRMED, AllocationStatuses.UNCONFIRMED))
    }
  }

  object ExternalSources {

    private val schemeRepository = SchemeYamlRepository

    def allFsbTypes = schemeRepository.getFsbTypes

    private val locationsAndVenuesRepository: LocationsWithVenuesRepository = LocationsWithVenuesInMemoryRepository

    def allVenues = Await.result(locationsAndVenuesRepository.venues.map(_.allValues.toList), 1 second)

    def venuesByLocation(location: String): List[Venue] = {
      val venues = locationsAndVenuesRepository.locationsWithVenuesList.map { list =>
        list.filter(lv => lv.name == location).flatMap(_.venues)
      }
      Await.result(venues, 1 second)
    }
  }

  //scalastyle :off
  val loremIpsum = """
    |Lorem ipsum dolor sit amet, consectetur adipiscing elit. Donec bibendum odio ac dictum tincidunt. Pellentesque at suscipit metus. Phasellus ac ante at dolor eleifend tincidunt efficitur ut purus. Donec pharetra leo sed malesuada luctus. Proin ac egestas turpis. Vivamus aliquam nunc ac dapibus pharetra. Maecenas dignissim maximus ligula, eu rutrum odio malesuada ac. Fusce euismod lobortis pretium. Ut blandit commodo erat, porttitor eleifend nisl consectetur sed. Duis varius nisi sit amet elit convallis interdum.
    |
    |Nam et gravida leo. Maecenas non elementum justo. Praesent nec congue purus. Cras luctus vitae velit at cursus. Maecenas aliquet mauris pulvinar, scelerisque diam vel, tincidunt diam. Curabitur vulputate elementum nulla non rutrum. Sed consequat urna eget mi vestibulum, nec placerat urna tempor.
    |
    |Praesent nec nibh felis. Vestibulum porttitor, risus vitae ultrices facilisis, tortor dui elementum dui, nec bibendum tellus diam non nulla. Fusce sagittis quam non feugiat pellentesque. Sed ac auctor quam. Sed nec sem accumsan sem facilisis tincidunt convallis tincidunt ante. Ut mi lorem, consectetur quis magna id, congue molestie dui. Pellentesque auctor, mi molestie feugiat commodo, dui nisl ornare nisi, nec tincidunt erat lacus quis lorem. Nulla rutrum rutrum velit, nec sodales tellus molestie aca
  """.stripMargin
  //scalastyle :on


}

//scalastyle:on number.of.methods

